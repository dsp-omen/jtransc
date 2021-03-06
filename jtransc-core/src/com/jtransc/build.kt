/*
 * Copyright 2016 Carlos Ballesteros Velasco
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jtransc

import com.jtransc.ast.*
import com.jtransc.ast.treeshaking.TreeShaking
import com.jtransc.error.InvalidOperationException
import com.jtransc.error.invalidOp
import com.jtransc.gen.GenTargetDescriptor
import com.jtransc.gen.GenTargetSubDescriptor
import com.jtransc.injector.Injector
import com.jtransc.input.AsmToAst
import com.jtransc.io.ProcessResult2
import com.jtransc.log.log
import com.jtransc.maven.MavenLocalRepository
import com.jtransc.time.measureProcess
import com.jtransc.time.measureTime
import com.jtransc.vfs.LocalVfs
import com.jtransc.vfs.MergedLocalAndJars
import com.jtransc.vfs.SyncVfsFile
import java.io.File

fun Iterable<GenTargetDescriptor>.locateTargetByName(target: String): GenTargetSubDescriptor {
	val parts = target.split(":")
	return GenTargetSubDescriptor(
		descriptor = this.firstOrNull { it.name == parts[0] } ?: throw Exception("Unknown target $target"),
		sub = parts.getOrElse(1) { "default" }
	)
}

enum class BuildBackend { ASM }
data class ConfigSubtarget(val subtarget: String)
data class ConfigTargetDirectory(val targetDirectory: String)
data class ConfigCaptureRunOutput(val captureRunOutput: Boolean)
data class ConfigRun(val run: Boolean)
data class ConfigLibraries(val libs: List<String>)
data class ConfigClassPaths(val classPaths: List<String>)
data class ConfigEntryPoint(val entrypoint: FqName)
data class ConfigMainClass(val mainClass: String)
data class ConfigInitialClasses(val initialClasses: List<String>)
data class ConfigResourcesVfs(val resourcesVfs: SyncVfsFile)

data class ConfigOutputFile(val output: String) {
	val outputFileBaseName by lazy { File(output).name }
}

data class ConfigOutputPath(val outputPath: SyncVfsFile)

class JTranscBuild(
	val injector: Injector,
	val target: GenTargetDescriptor,
	val entryPoint: String,
	val output: String,
	val subtarget: String,
	val settings: AstBuildSettings,
	val targetDirectory: String = System.getProperty("java.io.tmpdir")
) {
	val types = injector.get<AstTypes>()
	val configClassPaths = injector.get<ConfigClassPaths>()
	val backend = injector.get<BuildBackend>()
	val tempdir = System.getProperty("java.io.tmpdir")

	fun buildAndRunCapturingOutput() = _buildAndRun(captureRunOutput = true, run = true)
	fun buildAndRunRedirecting() = _buildAndRun(captureRunOutput = false, run = true)
	fun buildWithoutRunning() = _buildAndRun(captureRunOutput = false, run = false)
	fun buildAndRun(captureRunOutput: Boolean, run: Boolean = true) = _buildAndRun(captureRunOutput = captureRunOutput, run = run)

	class Result(val process: ProcessResult2)

	private fun _buildAndRun(captureRunOutput: Boolean = true, run: Boolean = false): Result {
		val classPaths2 = (settings.rtAndRtCore + target.extraLibraries.flatMap { MavenLocalRepository.locateJars(it) } + configClassPaths.classPaths).distinct()

		log("AllBuild.build(): language=$target, subtarget=$subtarget, entryPoint=$entryPoint, output=$output, targetDirectory=$targetDirectory")
		for (cp in classPaths2) log("ClassPath: $cp")

		// @TODO: We should be able to add these references to java.lang.Object using some kind of annotation!!
		val initialClasses: List<String> = listOf(
			java.lang.Object::class.java.name,
			java.lang.Void::class.java.name,
			java.lang.Byte::class.java.name,
			java.lang.Character::class.java.name,
			java.lang.Short::class.java.name,
			java.lang.Integer::class.java.name,
			java.lang.Long::class.java.name,
			java.lang.Float::class.java.name,
			java.lang.Double::class.java.name,
			java.lang.Class::class.java.name,
			java.lang.reflect.Method::class.java.name,
			java.lang.reflect.Field::class.java.name,
			java.lang.reflect.Constructor::class.java.name,
			java.lang.annotation.Annotation::class.java.name,
			java.lang.reflect.InvocationHandler::class.java.name,
			com.jtransc.internal.JTranscAnnotationBase::class.java.name,
			com.jtransc.JTranscWrapped::class.java.name,
			com.jtransc.lang.Int64::class.java.name,
			entryPoint.fqname.fqname
		)

		injector.mapInstances(
			ConfigClassPaths(classPaths2),
			ConfigInitialClasses(initialClasses), settings,
			ConfigSubtarget(subtarget),
			ConfigTargetDirectory(targetDirectory),
			ConfigOutputFile(output),
			ConfigCaptureRunOutput(captureRunOutput),
			ConfigRun(run),
			ConfigOutputPath(LocalVfs(File("$tempdir/out_ast"))),
			ConfigMainClass(entryPoint)
		)

		when (backend) {
			BuildBackend.ASM -> injector.mapImpl<AstClassGenerator, AsmToAst>()
			else -> invalidOp("Unsupported backend")
		}

		val programBase = measureProcess("Generating AST") {
			generateProgram()
		}

		val configTreeShaking = injector.get<ConfigTreeShaking>()
		val program = if (configTreeShaking.treeShaking) {
			TreeShaking(programBase, target.name, configTreeShaking.trace)
		} else {
			programBase
		}

		injector.mapInstance(program)
		injector.mapInstance(program, AstResolver::class.java)

		//val programDced = measureProcess("Simplifying AST") { SimpleDCE(program, programDependencies) }
		return target.build(injector)
	}

	fun generateProgram(): AstProgram {
		val injector: Injector = injector.get()
		val configClassNames: ConfigInitialClasses = injector.get()
		val configMainClass: ConfigMainClass = injector.get()
		val generator: AstClassGenerator = injector.get()
		val classNames = configClassNames.initialClasses
		val mainClass = configMainClass.mainClass

		val classPaths: List<String> = injector.get<ConfigClassPaths>().classPaths

		//println(classPaths)

		injector.mapInstances(
			ConfigEntryPoint(FqName(mainClass)),
			ConfigResourcesVfs(MergedLocalAndJars(classPaths))
		)
		val program = injector.get<AstProgram>()

		// Preprocesses classes
		classNames.forEach { program.addReference(AstType.REF(it), AstType.REF(it)) }

		log("Processing classes...")

		val (elapsed) = measureTime {
			while (program.hasClassToGenerate()) {
				val className = program.readClassToGenerate()

				val time = measureTime {
					try {
						val generatedClass = generator.generateClass(program, className.name)
						for (ref in References.get(generatedClass)) program.addReference(ref, className)
					}catch (e: InvalidOperationException) {
						System.err.println("ERROR! : " + e.message)
					}
				}

				//println("Ok(${time.time})");
			}

			// Reference default methods
			for (clazz in program.classes) {
				for (method in clazz.allDirectInterfaces.flatMap { it.methods }) {
					val methodRef = method.ref.withoutClass
					if (method.hasBody && !method.isStatic && !method.isClassOrInstanceInit && (clazz.getMethodInAncestors(methodRef) == null)) {
						clazz.add(generateDummyMethod(clazz, method.name, methodRef.type, false, AstVisibility.PUBLIC, method.ref))
					}
				}
			}

			// Add synthetic methods to abstracts to simulate in haxe
			// @TODO: Maybe we could generate those methods in haxe generator since the requirement for this
			// @TODO: is target dependant
			for (clazz in program.classes.filter { it.isAbstract }) {
				for (method in clazz.allMethodsToImplement.filter { clazz.getMethodInAncestors(it) == null }) {
					clazz.add(generateDummyMethod(clazz, method.name, method.type, false, AstVisibility.PUBLIC))
				}
			}
		}

		//for (dep in projectContext.deps2!!) println(dep)

		val methodCount = program.classes.sumBy { it.methods.size }

		log("Ok classes=${program.classes.size}, methods=$methodCount, time=$elapsed")

		return program
	}

	fun generateDummyMethod(containingClass: AstClass, name: String, methodType: AstType.METHOD, isStatic: Boolean, visibility: AstVisibility, bodyRef: AstMethodRef? = null) = AstMethod(
		containingClass = containingClass,
		id = -1,
		annotations = listOf(),
		name = name,
		methodType = methodType,
		generateBody = { null },
		signature = methodType.mangle(),
		genericSignature = methodType.mangle(),
		defaultTag = null,
		bodyRef = bodyRef,
		modifiers = AstModifiers.withFlags(AstModifiers.ACC_NATIVE, if (isStatic) AstModifiers.ACC_STATIC else 0).withVisibility(visibility),
		types = types
	)
}
