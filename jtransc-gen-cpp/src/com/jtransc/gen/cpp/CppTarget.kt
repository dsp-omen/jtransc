package com.jtransc.gen.cpp

import com.jtransc.ConfigLibraries
import com.jtransc.ConfigOutputFile
import com.jtransc.ConfigTargetDirectory
import com.jtransc.annotation.JTranscAddFileList
import com.jtransc.annotation.JTranscAddHeaderList
import com.jtransc.annotation.JTranscAddLibrariesList
import com.jtransc.annotation.JTranscAddMembersList
import com.jtransc.ast.*
import com.jtransc.ast.feature.GotosFeature
import com.jtransc.ast.feature.OptimizeFeature
import com.jtransc.ast.feature.SimdFeature
import com.jtransc.ast.feature.SwitchesFeature
import com.jtransc.error.InvalidOperationException
import com.jtransc.error.invalidOp
import com.jtransc.gen.GenTargetDescriptor
import com.jtransc.gen.GenTargetProcessor
import com.jtransc.gen.common.*
import com.jtransc.injector.Injector
import com.jtransc.injector.Singleton
import com.jtransc.io.ProcessResult2
import com.jtransc.target.Cpp
import com.jtransc.text.Indenter
import com.jtransc.text.quote
import com.jtransc.text.uquote
import com.jtransc.vfs.LocalVfs
import com.jtransc.vfs.LocalVfsEnsureDirs
import com.jtransc.vfs.SyncVfsFile
import java.io.File
import java.util.*

//const val CHECK_NPE = false
//const val CHECK_ARRAYS = true
//const val TRACING = true
//const val TRACING_JUST_ENTER = true

const val CHECK_NPE = true
const val CHECK_ARRAYS = true
const val TRACING = false
const val TRACING_JUST_ENTER = false

//const val TRACING = true
//const val TRACING_JUST_ENTER = true

data class ConfigCppOutput(val cppOutput: SyncVfsFile)

// @TODO: http://en.cppreference.com/w/cpp/language/eval_order
// @TODO: Use std::array to ensure it is deleted
object CppTarget : GenTargetDescriptor() {
	override val name = "cpp"
	override val longName = "cpp"
	override val sourceExtension = "cpp"
	override val outputExtension = "bin"
	override val extraLibraries = listOf<String>()
	override val extraClasses = listOf<String>()
	override val runningAvailable: Boolean = true
	override fun getProcessor(injector: Injector): GenTargetProcessor {
		injector.mapInstance(ConfigOutputFile("program.cpp"))
		val settings = injector.get<AstBuildSettings>()
		val configTargetDirectory = injector.get<ConfigTargetDirectory>()
		val configOutputFile = injector.get<ConfigOutputFile>()
		val targetFolder = LocalVfsEnsureDirs(File("${configTargetDirectory.targetDirectory}/jtransc-cpp"))
		injector.mapInstance(ConfigFeatureSet(CppFeatures))
		injector.mapImpl<CommonNames, CppNames>()
		injector.mapInstance(CommonGenFolders(settings.assets.map { LocalVfs(it) }))
		injector.mapInstance(ConfigTargetFolder(targetFolder))
		injector.mapInstance(ConfigSrcFolder(targetFolder))
		injector.mapInstance(ConfigOutputFile2(targetFolder[configOutputFile.outputFileBaseName].realfile))
		injector.mapImpl<CommonProgramTemplate, CommonProgramTemplate>()
		return injector.get<CppGenTargetProcessor>()
	}

	override fun getTargetByExtension(ext: String): String? = when (ext) {
		"exe" -> "cpp"
		"bin" -> "cpp"
		else -> null
	}
}

val CppFeatures = setOf(SwitchesFeature, GotosFeature)

@Singleton
class CppGenTargetProcessor(
	val injector: Injector,
	val settings: AstBuildSettings,
	val configTargetFolder: ConfigTargetFolder,
	val configOutputFile: ConfigOutputFile,
	val templateString: CommonProgramTemplate,
	val gen: GenCppGen
) : CommonGenTargetProcessor(gen) {
	var libraries = listOf<String>()
	override fun buildSource() {
		gen.writeProgram(configTargetFolder.targetFolder)
		templateString.setInfoAfterBuildingSource()
	}

	override fun compile(): ProcessResult2 {
		// -O0 = 23s && 7.2MB
		// -O4 = 103s && 4.3MB
		val debug = settings.debug
		val release = !debug
		val cmdAndArgs = arrayListOf<String>()
		cmdAndArgs += "clang++"
		cmdAndArgs += "-std=c++0x"
		cmdAndArgs += "-fms-compatibility-version=19.00"
		if (debug) cmdAndArgs += "-g"
		cmdAndArgs += if (debug) "-O0" else "-O3"
		cmdAndArgs += "-fexceptions"
		cmdAndArgs += "-Wno-parentheses-equality"
		cmdAndArgs += "-Wimplicitly-unsigned-literal"
		cmdAndArgs += "-frtti"
		cmdAndArgs += configOutputFile.output
		for (lib in injector.get<ConfigLibraries>().libs) {
			cmdAndArgs += "-l$lib"
		}

		println(cmdAndArgs)
		val result = LocalVfs(File(configTargetFolder.targetFolder.realpathOS)).exec(cmdAndArgs)
		//val result = LocalVfs(File(configTargetFolder.targetFolder.realpathOS)).exec("clang++", "-O0", "-g", "-fexceptions", "-Wno-parentheses-equality", "-fno-rtti", configOutputFile.output)
		//val result = LocalVfs(File(configTargetFolder.targetFolder.realpathOS)).exec("clang++", "-O4", "-g0", "-fexceptions", "-Wno-parentheses-equality", "-fno-rtti", configOutputFile.output)
		if (!result.success) {
			throw RuntimeException(result.outputString + result.errorString)
		}
		return ProcessResult2(result)
	}

	override fun run(redirect: Boolean): ProcessResult2 {
		val names = listOf("a.exe", "a", "a.out")
		val outFile = names.map { configTargetFolder.targetFolder[it] }.firstOrNull { it.exists } ?: invalidOp("Not generated output file $names")
		val result = LocalVfs(File(configTargetFolder.targetFolder.realpathOS)).exec(outFile.realpathOS)
		return ProcessResult2(result)
	}
}

@Singleton
class GenCppGen(injector: Injector) : GenCommonGen(injector) {
	override val allowAssignItself = true
	var lastClassId = 1
	val idsToClass = hashMapOf<Int, FqName>()
	val classesToId = hashMapOf<FqName, Int>()
	fun getClassId(fqname: FqName): Int {
		if (fqname !in classesToId) {
			val id = lastClassId++
			classesToId[fqname] = id
			idsToClass[id] = fqname
		}
		return classesToId[fqname]!!
	}

	fun generateTypeTableHeader() = Indenter.gen {
		line("struct REFLECT_CONSTRUCTOR", after2 = ";") {
			line("const wchar_t *desc;")
			line("const wchar_t *genericDesc;")
			line("int flags;")
		}

		line("struct REFLECT_METHOD", after2 = ";") {
			line("const wchar_t *name;")
			line("const wchar_t *desc;")
			line("const wchar_t *genericDesc;")
			line("int flags;")
		}

		line("struct REFLECT_FIELD", after2 = ";") {
			line("const wchar_t *name;")
			line("const wchar_t *desc;")
			line("const wchar_t *genericDesc;")
			line("int flags;")
		}

		line("struct TYPE_INFO", after2 = ";") {
			line("const int flags;")
			line("const int *subtypes;")
			line("const wchar_t *tname;")
			line("const int constructorsSize;")
			line("const REFLECT_CONSTRUCTOR* constructors;")
			line("const int methodsSize;")
			line("const REFLECT_METHOD* methods;")
			line("const int fieldsSize;")
			line("const REFLECT_FIELD* fields;")
			line("SOBJ (*dynamicNew)(int, std::vector<SOBJ>);")
			line("SOBJ (*dynamicInvoke)(int, SOBJ, std::vector<SOBJ>);")
			line("void *(*dynamicFieldPtr)(int, SOBJ);")
			//line("SOBJ (*dynamicGet)(int, SOBJ);")
			//line("void (*dynamicSet)(int, SOBJ, SOBJ);")
			line("const int superType;")
			line("const int interfaceCount;")
			line("const int *interfaces;")
		}

		line("struct TYPE_TABLE { static int count; static TYPE_INFO TABLE[$lastClassId]; };")

		line("const int TABLE_INFO_NULL[1] = {0};")
	}

	fun generateTypeTableFooter() = Indenter.gen {
		for (clazz in ordereredClasses) {
			val ids = (clazz.thisAndAncestors + clazz.allInterfacesInAncestors).distinct().map { classesToId[it.name] }.filterNotNull() + listOf(0)
			line("const int ${clazz.cppName}::TABLE_INFO[] = { ${ids.joinToString(", ")} };")
		}

		for (clazz in ordereredClasses) {
			val ids = (clazz.directInterfaces).distinct().map { classesToId[it.name] }.filterNotNull()
			line("const int ${clazz.cppName}::INTERFACES_COUNT = ${ids.size};")
			line("const int ${clazz.cppName}::INTERFACES[] = { ${ids.joinToString(", ")} };")
		}

		for (clazz in ordereredClasses) {
			val superId = (if (clazz.parentClass != null) classesToId[clazz.parentClass!!.name] else null) ?: -1
			line("const int ${clazz.cppName}::SUPER = $superId;")
		}

		line("int TYPE_TABLE::count = $lastClassId;")
		line("TYPE_INFO TYPE_TABLE::TABLE[$lastClassId] =", after2 = ";") {
			for (n in 0 until lastClassId) {
				val clazzName = idsToClass[n]
				val clazz = if (clazzName != null) program[clazzName] else null
				if (clazz != null) {
					line("${clazz.cppName}::INFO,")
				} else {
					line("{")
					indent {
						line(".flags = 0,")
						line(".subtypes = 0,")
						line(".tname = 0,")
						line(".constructorsSize = 0,")
						line(".constructors = 0,")
						line(".methodsSize = 0,")
						line(".methods = 0,")
						line(".fieldsSize = 0,")
						line(".fields = 0,")
						line(".dynamicNew = 0,")
						line(".dynamicInvoke = 0,")
						line(".dynamicFieldPtr = 0,")
						line(".superType = 0,")
						line(".interfaceCount = 0,")
						line(".interfaces = 0,")
					}
					line("},")
				}
			}
		}
		//line("""REFLECT_CONSTRUCTOR testConstructor = { L"()V", 0, [](std::vector<SOBJ> args) { return SOBJ(new java_lang_Object()); } };""")
	}

	val ordereredClasses = Unit.let {
		// (program.classes.filter { it.isInterface } + program.classes.filter { !it.isInterface }.flatMap {
		//	listOf(it) + it.thisAndAncestors.reversed()
		//}).distinct()
		val childrenMap = hashMapOf<AstClass, ArrayList<AstClass>>()
		for (current in program.classes) {
			val parent = current.parentClass
			if (parent != null) {
				val list = childrenMap.getOrPut(parent) { arrayListOf() }
				list += current
			}
		}

		val out = LinkedHashSet<AstClass>()

		fun explore(classes: List<AstClass>) {
			if (classes.isNotEmpty()) {
				for (clazz in classes) out += clazz
				explore(classes.flatMap { childrenMap[it] ?: arrayListOf() }.filter { it !in out })
			}
		}

		val roots = program.classes.filter { it.parentClass == null }
		explore(roots)

		out.toList()
	}

	override fun genBodyTrapsPrefix(): Indenter = indent { line("SOBJ J__exception__ = null;") }

	override fun genStmTryCatch(stm: AstStm.TRY_CATCH): Indenter = Indenter.gen {
		line("try") {
			line(stm.trystm.genStm())
		}
		line("catch (SOBJ J__i__exception__)") {
			line("J__exception__ = J__i__exception__;")
			line(stm.catch.genStm())
		}
	}

	// @TODO: intelliJ BUG
	val JTranscAddHeaderList_CLASS = JTranscAddHeaderList::class.java
	val JTranscAddFileList_CLASS = JTranscAddFileList::class.java

	internal fun writeProgram(output: SyncVfsFile) {
		val arrayTypes = listOf(
			"JA_B" to "int8_t",
			"JA_Z" to "int8_t",
			"JA_S" to "int16_t",
			"JA_C" to "uint16_t",
			"JA_I" to "int32_t",
			"JA_J" to "int64_t",
			"JA_F" to "float",
			"JA_D" to "double",
			"JA_L" to "SOBJ"
		)

		val mainClassFq = program.entrypoint
		val entryPointClass = FqName(mainClassFq.fqname)
		val entryPointFilePath = entryPointClass.targetFilePath

		val HEADER = Indenter.gen {
			// {{ HEADER }}
			val resourcesVfs = program.resourcesVfs

			for (clazz in program.classes) {
				for (includes in clazz.annotationsList.getTypedList(JTranscAddHeaderList::value).filter { it.target == "cpp" }) {
					for (header in includes.value) line(header)
				}
				for (files in clazz.annotationsList.getTypedList(JTranscAddFileList::value).filter { it.target == "cpp" }) {
					if (files.prepend.isNotEmpty()) {
						line(templateString.gen(resourcesVfs[files.prepend].readString(), process = files.process))
					}
				}
			}
		}


		val CLASS_REFERENCES = Indenter.gen {
			// {{ CLASS_REFERENCES }}
			for (clazz in ordereredClasses.filter { !it.isNative }) {
				getClassId(clazz.name)
				line(writeClassRef(clazz))
			}
		}

		val TYPE_TABLE_HEADERS = Indenter.gen {
			// {{ TYPE_TABLE_HEADERS }}
			line(generateTypeTableHeader())
		}

		val ARRAY_TYPES = Indenter.gen {
			// {{ ARRAY_TYPES }}
			for ((name, type) in arrayTypes) line("struct $name;")
		}

		val ARRAY_HEADERS = Indenter.gen {
			// {{ ARRAY_HEADERS }}
			for (clazz in ordereredClasses.filter { !it.isNative }) {
				line(writeClassHeader(clazz))
				if (clazz.fqname == "java.lang.Object") {
					line("struct JA_0 : public java_lang_Object { public:")
					indent {
						line("void *_data;")
						line("int length;")
						line("int elementSize;")
						line("std::wstring desc;")
						line("JA_0(int len, int esize, std::wstring d) : length(len), elementSize(esize), desc(d) {")
						indent {
							line("this->__INSTANCE_CLASS_ID = 1;")
							line("this->_data = (void*)::malloc(esize * (len + 1));")
							line("::memset(this->_data, 0, (len + 1) * esize);")
						}
						line("};")
						line("~JA_0() { ::free(_data); }")
						line("void *getOffsetPtr(int offset) { return ((char *)_data) + offset * elementSize; }")
						line("static void copy(JA_0* src, int srcpos, JA_0* dst, int dstpos, int len)") {
							line("::memmove(dst->getOffsetPtr(dstpos), src->getOffsetPtr(srcpos), len * src->elementSize);")
						}
						line("virtual SOBJ M_getClass___Ljava_lang_Class_();")
					}
					line("};")

					for ((name, type) in arrayTypes) {
						val c = name.substring(name.length - 1)
						if (name == "JA_Z") {
							line("""struct JA_Z : public JA_B { public: JA_Z(int size, std::wstring desc = L"[Z") : JA_B(size, desc) { }; };""")
						} else {
							line("struct $name : public JA_0 { public:")
							indent {
								line("""$name(int size, std::wstring desc = L"[$c") : JA_0(size, sizeof($type), desc)""", after2 = ";") {
									//line("this->__INSTANCE_CLASS_ID = ${getClassId(name.fqname)};")
								}
								line("""inline void checkBounds(int offset)""", after2 = ";") {
									line("""if (offset < 0 || offset >= length)""") {
										line("""std::wstringstream os;""")
										line("""os << L"Out of bounds " << offset << L" " << length;""")
										line("""throw os.str();""")
									}
								}

								// @TODO: Proper reference
								line("virtual SOBJ M_clone___Ljava_lang_Object_()", after2 = ";") {
									line("""auto out = new $name(this->length, this->desc);""")
									line("""JA_0::copy(this, 0, out, 0, this->length);""")
									line("""return SOBJ(out);""")
								}

								if (CHECK_ARRAYS) {
									line("""inline void fastSet(int offset, $type v) { checkBounds(offset); (($type*)(this->_data))[offset] = v; };""")
									line("""inline $type fastGet(int offset) { checkBounds(offset); return (($type*)(this->_data))[offset]; }""")
								} else {
									line("""inline void fastSet(int offset, $type v) { (($type*)(this->_data))[offset] = v; };""")
									line("""inline $type fastGet(int offset) { return (($type*)(this->_data))[offset]; }""")
								}

								line("""void set(int offset, $type v) { checkBounds(offset); fastSet(offset, v); };""")
								line("""$type get(int offset) { checkBounds(offset); return fastGet(offset); }""")

								//line("void setArray(int start, std::vector<$type> arrays)") {
								line("void setArray(int start, int size, const $type *arrays)") {
									line("for (int n = 0; n < size; n++) this->set(start + n, arrays[n]);")
								}

								line("static $name *fromVector($type *data, int count)", after2 = ";") {
									line("auto out = new $name(count);")
									line("out->setArray(0, count, (const $type *)data);")
									line("return out;")
								}

								if (name == "JA_L") {
									line("std::vector<SOBJ> getVector()") {
										line("int len = this->length;")
										line("std::vector<SOBJ> out(len);")
										line("for (int n = 0; n < len; n++) out[n] = this->fastGet(n);")
										line("return out;")
									}

									line("""static JA_0* createMultiSure(std::wstring desc, std::vector<int32_t> sizes)""") {
										line("""if (sizes.size() == 0) throw L"Multiarray with zero sizes";""")

										line("""int32_t size = sizes[0];""")

										line("if (sizes.size() == 1)") {
											line("""if (desc == std::wstring(L"[Z")) return new JA_Z(size); """)
											line("""if (desc == std::wstring(L"[B")) return new JA_B(size); """)
											line("""if (desc == std::wstring(L"[S")) return new JA_S(size); """)
											line("""if (desc == std::wstring(L"[C")) return new JA_C(size); """)
											line("""if (desc == std::wstring(L"[I")) return new JA_I(size); """)
											line("""if (desc == std::wstring(L"[J")) return new JA_J(size); """)
											line("""if (desc == std::wstring(L"[F")) return new JA_F(size); """)
											line("""if (desc == std::wstring(L"[D")) return new JA_D(size); """)
											line("""throw L"Invalid multiarray"; """)
										}

										// std::vector<decltype(myvector)::value_type>(myvector.begin()+N, myvector.end()).swap(myvector);


										line("""auto out = new JA_L(size, desc);""")
										line("""auto subdesc = desc.substr(1);""")
										line("""auto subsizes = std::vector<int32_t>(sizes.begin() + 1, sizes.end());""")
										line("for (int n = 0; n < size; n++)") {
											line("""out->set(n, SOBJ(createMultiSure(subdesc, subsizes)));""")
										}
										line("return out;");
									}
								}
							}
							line("};")
						}
					}
				}
			}
		}

		val impls = Indenter.gen {
			for (clazz in ordereredClasses.filter { !it.isNative }) {
				if (clazz.implCode != null) {
					line(clazz.implCode!!)
				} else {
					line(writeClassImpl(clazz))
				}
			}
		}

		val STRINGS = Indenter.gen {
			// {{ STRINGS }}
			for (gs in names.getGlobalStrings()) {
				line("""STRINGLIT   STRINGLIT_${gs.id} = { L${gs.str.uquote()}, ${gs.str.length}, false, 0 };""")
			}
		}

		val CLASSES_IMPL = Indenter.gen {
			// {{ CLASSES_IMPL2 }}
			line(impls)
		}

		val TYPE_TABLE_FOOTER = Indenter.gen {
			// {{ TYPE_TABLE_FOOTER }}
			line(generateTypeTableFooter())
		}

		val MAIN = Indenter.gen {
			// {{ MAIN }}
			line(writeMain())
		}

		val classesIndenter = Indenter.gen {
			if (settings.debug) {
				line("#define DEBUG 1")
			} else {
				line("#define RELEASE 1")
			}
			if (TRACING_JUST_ENTER) line("#define TRACING_JUST_ENTER")
			if (TRACING) line("#define TRACING")
			line(templateString.gen(program.resourcesVfs["cpp/Base.cpp"].readString(), extra = hashMapOf(
				"HEADER" to HEADER.toString(),
				"CLASS_REFERENCES" to CLASS_REFERENCES.toString(),
				"TYPE_TABLE_HEADERS" to TYPE_TABLE_HEADERS.toString(),
				"ARRAY_TYPES" to ARRAY_TYPES.toString(),
				"ARRAY_HEADERS" to ARRAY_HEADERS.toString(),
				"CLASSES_IMPL" to CLASSES_IMPL.toString(),
				"STRINGS" to STRINGS.toString(),
				"TYPE_TABLE_FOOTER" to TYPE_TABLE_FOOTER.toString(),
				"MAIN" to MAIN.toString()
			)))
		}


		output[outputFile] = classesIndenter.toString()

		injector.mapInstance(ConfigEntryPointClass(entryPointClass))
		injector.mapInstance(ConfigEntryPointFile(entryPointFilePath))
		injector.mapInstance(ConfigCppOutput(output[outputFile]))

		println(output[outputFile].realpathOS)
	}

	val AstClass.cppName: String get() = names.getClassFqNameForCalling(this.name)
	val AstType.REF.cppName: String get() = names.getClassFqNameForCalling(this.name)
	val AstMethod.cppName: String get() = names.getNativeName(this)
	val AstField.cppName: String get() = names.getNativeName(this)
	val AstType.cppString: String get() = names.getTypeStringForCpp(this)
	val AstType.underlyingCppString: String get() = getUnderlyingType(this)

	fun writeMain(): Indenter = Indenter.gen {
		line("int main(int argc, char *argv[])") {
			line("""TRACE_REGISTER("::main");""")
			line("try") {
				line("N::startup();")
				line(names.buildStaticInit(program[program.entrypoint]))
				line(program.entrypoint.ref().cppName + "::S_main___Ljava_lang_String__V(N::strEmptyArray());")
			}
			line("catch (char const *s)") {
				line("""std::cout << "ERROR char const* " << s << "\n";""")
			}
			line("catch (std::wstring s)") {
				line("""std::wcout << L"ERROR std::wstring " << s << L"\n";""")
			}
			line("catch (java_lang_Throwable *s)") {
				line("""std::wcout  << L"java_lang_Throwable:" << L"\n";""")
				line("""printf("Exception: %p\n", (void*)s);""")
			}
			line("catch (SOBJ s)") {
				line("""std::wcout << L"ERROR SOBJ " << N::istr2(s.get()->M_toString___Ljava_lang_String_()) << L"\n";""")
			}
			line("catch (...)") {
				line("""std::wcout << L"ERROR unhandled unknown exception\n";""")
			}
			line("return 0;")
		}
	}

	fun writeClassRef(clazz: AstClass): Indenter = Indenter.gen {
		setCurrentClass(clazz)
		line("struct ${clazz.cppName};")
	}

	fun writeClassHeader(clazz: AstClass): Indenter = Indenter.gen {
		setCurrentClass(clazz)
		val directImplementing = clazz.allInterfacesInAncestors - (clazz.parentClass?.allInterfacesInAncestors ?: listOf())
		val directExtendingAndImplementing = (clazz.parentClassList + directImplementing)

		val parts = if (clazz.isInterface) {
			""
		} else if (clazz.fqname == "java.lang.Object") {
			"public std::enable_shared_from_this<java_lang_Object>"
		} else {
			directExtendingAndImplementing.map { "public ${it.cppName}" }.joinToString(", ")
		}

		line("struct ${clazz.cppName}${if (parts.isNotEmpty()) " : $parts " else " "} { public:")
		indent {
			JTranscAddMembersList::class.java // @TODO: Kotlin bug!
			for (member in clazz.annotationsList.getTypedList(JTranscAddMembersList::value).filter { it.target == "cpp" }.flatMap { it.value.toList() }) {
				line(member)
			}

			if (clazz.fqname == "java.lang.Object") {
				line("int __INSTANCE_CLASS_ID;")
				line("SOBJ sptr() { return shared_from_this(); };")
				//line("""~${clazz.cppName}() { printf("%p: %d\n", this, this->__INSTANCE_CLASS_ID); ((java_lang_Object*)this)->M_finalize___V(); }""")
				//line("""~${clazz.cppName}() { (dynamic_cast<java_lang_Object*>(this))->M_finalize___V(); }""") // @TODO: This is not working!
			}
			for (field in clazz.fields) {
				// destructor
				val normalStatic = if (field.isStatic) "static " else ""
				//val add = if (field.isStatic) ""
				val add = ""
				val btype = field.type.cppString
				val type = if (btype == "SOBJ" && field.isWeak) "WOBJ" else btype
				line("$normalStatic$type ${field.jsName}$add;")
			}

			// constructor
			line("${clazz.cppName}() ") {
				if (!clazz.isInterface) {
					line("this->__INSTANCE_CLASS_ID = ${getClassId(clazz.name)};")
					for (field in clazz.fields.filter { !it.isStatic }) {
						val cst = if (field.hasConstantValue) names.escapeConstant(field.constantValue) else "0"
						line("this->${field.jsName} = $cst;")
					}
				}
			}
			for (method in clazz.methods) {
				val type = method.methodType
				val argsString = type.args.map { it.type.cppString + " " + it.name }.joinToString(", ")
				val zero = if (clazz.isInterface && !method.isStatic) " = 0" else ""
				val inlineNone = if (method.isInline) "inline " else ""
				val virtualStatic = if (method.isStatic) "static " else "virtual "
				line("$inlineNone$virtualStatic${method.returnTypeWithThis.cppString} ${method.cppName}($argsString)$zero;")
			}
			for (parentMethod in directImplementing.flatMap { it.methods }) {
				val type = parentMethod.methodType
				val returnStr = if (type.retVoid) "" else "return "
				val argsString = type.args.map { it.type.cppString + " " + it.name }.joinToString(", ")
				val argsCallString = type.args.map { it.name }.joinToString(", ")
				val callingMethod = clazz.getMethodInAncestors(parentMethod.ref.withoutClass)
				if (callingMethod != null) {
					line("virtual ${parentMethod.returnTypeWithThis.cppString} ${parentMethod.cppName}($argsString) { $returnStr this->${callingMethod.cppName}($argsCallString); }")
				}
			}
			line("static bool SI_once;")
			line("static void SI();")

			val ids = (clazz.thisAndAncestors + clazz.allInterfacesInAncestors).distinct().map { classesToId[it.name] }.filterNotNull() + listOf(0)
			line("static const int TABLE_INFO[${ids.size}];")
			line("static const int SUPER;")
			line("static const int INTERFACES_COUNT;")
			line("static const int INTERFACES[${ids.size}];")
			line("static const REFLECT_CONSTRUCTOR CONSTRUCTORS[${clazz.constructors.size}];")
			line("static const REFLECT_METHOD      METHODS[${clazz.methodsWithoutConstructors.size}];")
			line("static const REFLECT_FIELD       FIELDS[${clazz.fields.size}];")
			line("static const wchar_t *NAME;")
			line("static const TYPE_INFO INFO;")

			line("static SOBJ DYNAMIC_NEW(int index, std::vector<SOBJ> args);")
			line("static SOBJ DYNAMIC_INVOKE(int index, SOBJ target, std::vector<SOBJ> args);")
			line("static void *DYNAMIC_FIELD_PTR(int index, SOBJ target);")
			//line("static SOBJ DYNAMIC_GET(int index, SOBJ target);")
			//line("static void DYNAMIC_SET(int index, SOBJ target, SOBJ value);")

			//line("static constexpr int DYNAMIC_INVOKE[${ids.size}] = { ${ids.joinToString(", ")} };")

			//line("static int[] TABLE_INFO = ********************;")
		}
		line("};")
		line("const wchar_t *${clazz.cppName}::NAME = L${clazz.fqname.quote()};")
		line("const REFLECT_CONSTRUCTOR ${clazz.cppName}::CONSTRUCTORS[] = ", after2 = ";") {
			for ((index, c) in clazz.constructors.withIndex()) {
				line("""{ .desc = L"${c.desc}", .genericDesc = L"${c.genericSignature}", .flags = ${c.modifiers.acc} },""")
			}
		}
		line("const REFLECT_METHOD ${clazz.cppName}::METHODS[] = ", after2 = ";") {
			for ((index, c) in clazz.methodsWithoutConstructors.withIndex()) {
				line("""{ .name = L${c.name.quote()}, .desc = L${c.desc.quote()}, .genericDesc = L"${c.genericSignature}", .flags =${c.modifiers.acc} },""")
			}
		}
		line("const REFLECT_FIELD ${clazz.cppName}::FIELDS[] = ", after2 = ";") {
			for ((index, c) in clazz.fields.withIndex()) {
				line("""{ .name = L${c.name.quote()}, .desc = L${c.desc.quote()}, .genericDesc = L"${c.genericSignature}", .flags = ${c.modifiers.acc} },""")
			}
		}
		line("SOBJ ${clazz.cppName}::DYNAMIC_NEW(int index, std::vector<SOBJ> args)") {
			if (clazz.constructors.isEmpty()) {
				line("return SOBJ(NULL);")
			} else {
				line("SOBJ out = SOBJ(new ${clazz.cppName}());")
				line("switch (index)") {
					for ((index, c) in clazz.constructors.withIndex()) {
						val args = c.methodType.args.map {
							val extract = "args[${it.index}]"
							val type = it.type
							when (type) {
								is AstType.Primitive -> N_unbox(type, extract)
								else -> "$extract"
							}
						}.joinToString(", ")
						line("""case $index: GET_OBJECT(${clazz.cppName}, out)->${c.cppName}($args); break;""")
					}
				}
				line("return out;")
			}
		}
		line("SOBJ ${clazz.cppName}::DYNAMIC_INVOKE(int index, SOBJ obj, std::vector<SOBJ> args)") {
			if (clazz.methodsWithoutConstructors.isNotEmpty()) {
				line(names.buildStaticInit(clazz))
				line("switch (index)") {
					// REFLECT_METHOD
					for ((index, method) in clazz.methodsWithoutConstructors.withIndex()) {
						val args = method.methodType.args.map { N_unbox(it.type, "args[${it.index}]") }.joinToString(", ")
						val rettype = method.methodType.ret

						val callUnboxed = if (method.isStatic) {
							"${clazz.cppName}::${method.cppName}($args)"
						} else {
							"GET_OBJECT(${clazz.cppName}, obj)->${method.cppName}($args)"
						}

						val callBoxed = when (rettype) {
							is AstType.VOID -> "$callUnboxed; return N::boxVoid();"
							else -> "return " + N_box(rettype, callUnboxed) + ";"
						}

						//line("""case $index: printf("CALLING ${clazz.cppName}::${method.cppName}\n"); $callBoxed""")
						line("""case $index: $callBoxed""")
					}
				}
			}

			line("return SOBJ(NULL);")
		}

		line("void *${clazz.cppName}::DYNAMIC_FIELD_PTR(int index, SOBJ obj)") {
			if (clazz.fields.isNotEmpty()) {
				line(names.buildStaticInit(clazz))
				line("switch (index)") {
					for ((index, field) in clazz.fields.withIndex()) {
						if (field.isStatic) {
							//line("""case $index: printf("DYNAMIC_FIELD_PTR:${clazz.cppName}::${field.cppName}\n"); return (void *)&(${clazz.cppName}::${field.cppName});""")
							line("""case $index: return (void *)&(${clazz.cppName}::${field.cppName});""")
						} else {
							//line("""case $index: printf("DYNAMIC_FIELD_PTR:${clazz.cppName}::${field.cppName}\n"); return (void *)&(GET_OBJECT(${clazz.cppName}, obj)->${field.cppName});""")
							line("""case $index: return (void *)&(GET_OBJECT(${clazz.cppName}, obj)->${field.cppName});""")
						}
					}
				}
			}
			line("return NULL;")
		}

		//line("SOBJ ${clazz.cppName}::DYNAMIC_GET(int index, SOBJ target)") {
		//	line("return SOBJ(NULL);")
		//}
//
		//line("void ${clazz.cppName}::DYNAMIC_SET(int index, SOBJ target, SOBJ value)") {
		//	//line("return SOBJ(NULL);")
		//}

		//line("const TYPE_INFO ${clazz.cppName}::INFO = { ${clazz.cppName}::TABLE_INFO, ${clazz.cppName}::NAME, ${clazz.constructors.size}, ${clazz.cppName}::CONSTRUCTORS, ${clazz.methodsWithoutConstructors.size}, ${clazz.cppName}::METHODS, ${clazz.cppName}::DYNAMIC_NEW, ${clazz.cppName}::DYNAMIC_INVOKE, ${clazz.cppName}::SUPER, ${clazz.cppName}::INTERFACES_COUNT, ${clazz.cppName}::INTERFACES };")
		line("const TYPE_INFO ${clazz.cppName}::INFO = ", after2 = ";") {
			line(".flags = ${clazz.modifiers.acc},")
			line(".subtypes = ${clazz.cppName}::TABLE_INFO,")
			line(".tname = ${clazz.cppName}::NAME,")
			line(".constructorsSize = ${clazz.constructors.size},")
			line(".constructors = ${clazz.cppName}::CONSTRUCTORS,")
			line(".methodsSize = ${clazz.methodsWithoutConstructors.size},")
			line(".methods = ${clazz.cppName}::METHODS,")
			line(".fieldsSize = ${clazz.fields.size},")
			line(".fields = ${clazz.cppName}::FIELDS,")
			line(".dynamicNew = ${clazz.cppName}::DYNAMIC_NEW,")
			line(".dynamicInvoke = ${clazz.cppName}::DYNAMIC_INVOKE,")
			line(".dynamicFieldPtr = ${clazz.cppName}::DYNAMIC_FIELD_PTR,")
			//line(".dynamicGet = ${clazz.cppName}::DYNAMIC_GET,")
			//line(".dynamicSet = ${clazz.cppName}::DYNAMIC_SET,")
			line(".superType = ${clazz.cppName}::SUPER,")
			line(".interfaceCount = ${clazz.cppName}::INTERFACES_COUNT,")
			line(".interfaces = ${clazz.cppName}::INTERFACES")
		}
	}

	fun writeClassImpl(clazz: AstClass): Indenter = Indenter.gen {
		setCurrentClass(clazz)
		//line("Class: $clazz")

		for (field in clazz.fields) {
			line(writeField(field))
		}

		for (method in clazz.methods) {
			if (!clazz.isInterface || method.isStatic) {
				try {
					line(writeMethod(method))
				} catch (e: Throwable) {
					throw RuntimeException("Couldn't generate method $method for class $clazz due to ${e.message}", e)
				}
			}
		}

		line("bool ${clazz.cppName}::SI_once = false;")
		line("void ${clazz.cppName}::SI() {")
		indent {
			line("if (SI_once) return;")
			line("""TRACE_REGISTER("${clazz.cppName}::SI");""")
			line("SI_once = true;")
			for (field in clazz.fields.filter { it.isStatic }) {
				if (field.isStatic) {
					val cst = if (field.hasConstantValue) names.escapeConstant(field.constantValue) else "0"
					line("${clazz.cppName}::${field.cppName} = $cst;")
				}
			}

			for (ci in clazz.methods.filter { it.isClassInit }) {
				line("${ci.cppName}();")
			}
		}
		line("};")
	}

	fun writeField(field: AstField): Indenter = Indenter.gen {
		val clazz = field.containingClass
		if (field.isStatic) {
			line("${field.type.cppString} ${clazz.cppName}::${field.cppName} = 0;")
		}
	}

	val FEATURE_FOR_FUNCTION_WITH_TRAPS = setOf(OptimizeFeature, SwitchesFeature, SimdFeature)
	val FEATURE_FOR_FUNCTION_WITHOUT_TRAPS = (FEATURE_FOR_FUNCTION_WITH_TRAPS + GotosFeature).toSet()

	override fun genBody2WithFeatures(body: AstBody): Indenter {
		if (body.traps.isNotEmpty()) {
			return features.apply(body, FEATURE_FOR_FUNCTION_WITH_TRAPS, settings, types).genBody()
		} else {
			return features.apply(body, FEATURE_FOR_FUNCTION_WITHOUT_TRAPS, settings, types).genBody()
		}
	}

	fun writeMethod(method: AstMethod): Indenter = Indenter.gen {
		val clazz = method.containingClass
		val type = method.methodType

		val argsString = type.args.map { it.type.cppString + " " + it.name }.joinToString(", ")

		line("${method.returnTypeWithThis.cppString} ${clazz.cppName}::${method.cppName}($argsString)") {
			if (method.name == "finalize") {
				//line("""std::cout << "myfinalizer\n"; """);
			}

			if (!method.isStatic) {
				//line("""SOBJ _this(this);""")
			}

			line("""const wchar_t *FUNCTION_NAME = L"${method.containingClass.name}::${method.name}::${method.desc}";""")
			line("""TRACE_REGISTER(FUNCTION_NAME);""")

			setCurrentMethod(method)
			val body = method.body

			fun genJavaBody() = Indenter.gen {
				if (body != null) {
					line(this@GenCppGen.genBody2WithFeatures(body))
				} else {
					line("throw \"Empty BODY : ${method.containingClass.name}::${method.name}::${method.desc}\";");
				}
			}

			val bodies = method.getNativeBodies("cpp")

			val nonDefaultBodies = bodies.filterKeys { it != "" }
			val defaultBody = bodies[""] ?: genJavaBody()

			if (nonDefaultBodies.size > 0) {
				for ((cond, nbody) in nonDefaultBodies) {
					line("#ifdef $cond")
					indent {
						line(nbody)
					}
				}
				line("#else")
				indent {
					line(defaultBody)
				}
				line("#endif")
			} else {
				line(defaultBody)
			}

			if (method.methodVoidReturnThis) line("return this->sptr();")
		}
	}

	override fun processCallArg(e: AstExpr, str: String): String {
		return "((" + e.type.cppString + ")(" + str + "))"
	}

	override fun genBodyLocal(local: AstLocal): Indenter = indent {
		line("${local.type.cppString} ${local.nativeName} = ${local.type.nativeDefaultString};")
	}

	private fun generatePositionString() = context.positionString

	override fun genExprArrayLength(e: AstExpr.ARRAY_LENGTH): String = "((JA_0*)${e.array.genNotNull()}.get())->length"
	override fun N_AGET_T(arrayType: AstType.ARRAY, elementType: AstType, array: String, index: String): String {
		val getMethod = if (context.useUnsafeArrays) "get" else "fastGet"
		return "((${getUnderlyingType(arrayType)})(N::ensureNpe($array, FUNCTION_NAME).get()))->$getMethod($index)"
	}

	override fun N_ASET_T(arrayType: AstType.ARRAY, elementType: AstType, array: String, index: String, value: String): String {
		val setMethod = if (context.useUnsafeArrays) "set" else "fastSet"
		return "((${getUnderlyingType(arrayType)})(N::ensureNpe($array, FUNCTION_NAME).get()))->$setMethod($index, $value);"
		//return "((${arrayType.cppString})($array.get()))->set($index, (${arrayType.element.cppString})($value));"
	}

	//lA6 = (OBJECT_SHARED)(((JA_L*)((lA3).get()))->get(lI5)));

	private fun isThisOrThisWithCast(e: AstExpr): Boolean {
		return when (e) {
			is AstExpr.THIS -> true
			is AstExpr.CAST -> if (e.to == this.mutableBody.method.containingClass.astType) {
				isThisOrThisWithCast(e.expr.value)
			} else {
				false
			}
			else -> false
		}
	}

	override fun genExprCallBaseInstance(e2: AstExpr.CALL_INSTANCE, clazz: AstType.REF, refMethodClass: AstClass, method: AstMethodRef, methodAccess: String, args: List<String>): String {
		//return "((${refMethodClass.cppName}*)(${e2.obj.genNotNull()}.get()))$methodAccess(${args.joinToString(", ")})"
		if (isThisOrThisWithCast(e2.obj.value)) {
			return "this$methodAccess(${args.joinToString(", ")})"
		} else {
			val objStr = "${e2.obj.genNotNull()}"
			return "(dynamic_cast<${refMethodClass.cppName}*>(N::ensureNpe($objStr, FUNCTION_NAME).get()))$methodAccess(${args.joinToString(", ")})"
		}
	}

	override fun genExprCallBaseSuper(e2: AstExpr.CALL_SUPER, clazz: AstType.REF, refMethodClass: AstClass, method: AstMethodRef, methodAccess: String, args: List<String>): String {
		val superMethod = refMethodClass[method.withoutClass] ?: invalidOp("Can't find super for method : $method")
		//val base = names.getClassFqNameForCalling(superMethod.containingClass.name) + ".prototype"
		//val argsString = (listOf("this") + args).joinToString(", ")

		//return "(dynamic_cast<${refMethodClass.cppName}*>((${e2.obj.genNotNull()}).get()))$methodAccess(${args.joinToString(", ")})"

		return "${refMethodClass.ref.cppName}::${superMethod.cppName}(${args.joinToString(", ")})"
	}

	override fun genExprCallBaseStatic(e2: AstExpr.CALL_STATIC, clazz: AstType.REF, refMethodClass: AstClass, method: AstMethodRef, methodAccess: String, args: List<String>): String {
		val className = method.containingClassType.fqname
		val methodName = method.name
		return if (className == Cpp::class.java.name && methodName.endsWith("_raw")) {
			val arg = e2.args[0].value
			if (arg !is AstExpr.LITERAL || arg.value !is String) invalidOp("Raw call $e2 has not a string literal! but ${args[0]}")
			val base = templateString.gen((arg.value as String))
			when (methodName) {
				"v_raw" -> "$base"
				"o_raw" -> "$base"
				"z_raw" -> "$base"
				"i_raw" -> "$base"
				"d_raw" -> "$base"
				"s_raw" -> "N::str($base)"
				else -> "$base"
			}
		} else {
			super.genExprCallBaseStatic(e2, clazz, refMethodClass, method, methodAccess, args)
		}
	}

	override fun genExprThis(e: AstExpr.THIS): String {
		return genExprThis()
	}

	fun genExprThis(): String {
		return "this->sptr()"
	}

	override fun genStmSetFieldInstance(stm: AstStm.SET_FIELD_INSTANCE): Indenter {
		return super.genStmSetFieldInstance(stm)
	}

	override fun genExprMethodClass(e: AstExpr.METHOD_CLASS): String {
		return "N::dummyMethodClass()"
	}

	override fun N_i2b(str: String) = "((int8_t)($str))"
	override fun N_i2c(str: String) = "((uint16_t)($str))"
	override fun N_i2s(str: String) = "((int16_t)($str))"
	override fun N_f2i(str: String) = "((int32_t)($str))"
	override fun N_d2i(str: String) = "((int32_t)($str))"
	//override fun N_i(str: String) = "((int32_t)($str))"
	override fun N_i(str: String) = "$str"

	override fun N_idiv(l: String, r: String) = N_func("idiv", "$l, $r")
	override fun N_irem(l: String, r: String) = N_func("irem", "$l, $r")
	override fun N_iushr(l: String, r: String) = N_func("iushr", "$l, $r")
	override fun N_frem(l: String, r: String) = "::fmod($l, $r)"
	override fun N_drem(l: String, r: String) = "::fmod($l, $r)"

	override fun N_ladd(l: String, r: String) = "(($l) + ($r))"
	override fun N_lsub(l: String, r: String) = "(($l) - ($r))"
	override fun N_lmul(l: String, r: String) = "(($l) * ($r))"
	override fun N_ldiv(l: String, r: String) = "N::ldiv($l, $r)"
	override fun N_lrem(l: String, r: String) = "N::lrem($l, $r)"
	override fun N_lshl(l: String, r: String) = "(($l) << ($r))"
	override fun N_lshr(l: String, r: String) = "(($l) >> ($r))"
	override fun N_lushr(l: String, r: String) = "(int64_t)((uint64_t)($l) >> ($r))"
	override fun N_lor(l: String, r: String) = "(($l) | ($r))"
	override fun N_lxor(l: String, r: String) = "(($l) ^ ($r))"
	override fun N_land(l: String, r: String) = "(($l) & ($r))"

	//override fun N_obj_eq(l: String, r: String) = "(((void*)$l) == ((void*)$r))"
	//override fun N_obj_ne(l: String, r: String) = "(((void*)$l) != ((void*)$r))"

	override fun N_obj_eq(l: String, r: String) = "(($l) == ($r))"
	override fun N_obj_ne(l: String, r: String) = "(($l) != ($r))"

	override fun genStmSetFieldStaticActual(stm: AstStm.SET_FIELD_STATIC, left: String, field: AstFieldRef, right: String): Indenter = indent {
		line("$left = (${field.type.cppString})($right);")
	}

	override fun genStmReturnVoid(stm: AstStm.RETURN_VOID): Indenter = Indenter.gen {
		line(if (context.method.methodVoidReturnThis) "return " + genExprThis() + ";" else "return;")
	}

	override fun genStmReturnValue(stm: AstStm.RETURN): Indenter = Indenter.gen {
		line("return (${context.method.returnTypeWithThis.cppString})${stm.retval.genExpr()};")
	}

	override fun N_is(a: String, b: AstType): String = when (b) {
		is AstType.REF -> N_func("is", "($a), ${getClassId(b.name)}")
		is AstType.ARRAY -> N_func("isArray", "($a), L${b.mangle().quote()}")
		else -> N_func("isUnknown", """$a, "Unsupported $b"""")
	}

	override fun genExprFieldInstanceAccess(e: AstExpr.FIELD_INSTANCE_ACCESS): String {
		if (isThisOrThisWithCast(e.expr.value)) {
			return names.buildInstanceField("this", fixField(e.field))
		} else {
			return names.buildInstanceField("((" + e.field.containingTypeRef.underlyingCppString + ")(N::ensureNpe(" + e.expr.genNotNull() + ", FUNCTION_NAME).get()))", fixField(e.field))
		}
	}

	override fun actualSetField(stm: AstStm.SET_FIELD_INSTANCE, _left: String, _right: String): String {
		val left = if (stm.left.value is AstExpr.THIS) {
			names.buildInstanceField("this", fixField(stm.field))
		} else {
			names.buildInstanceField("((${stm.field.containingTypeRef.underlyingCppString})N::ensureNpe(" + stm.left.genExpr() + ", FUNCTION_NAME).get())", fixField(stm.field))
		}
		val right = "(${stm.field.type.cppString})((${stm.field.type.cppString})(" + stm.expr.genExpr() + "))"

		return "$left = $right;"
	}

	override fun actualSetLocal(stm: AstStm.SET_LOCAL, localName: String, exprStr: String): String {
		return "$localName = (${stm.local.type.cppString})($exprStr);"
	}

	override fun createArraySingle(e: AstExpr.NEW_ARRAY, desc: String): String {
		return if (e.type.elementType !is AstType.Primitive) {
			"SOBJ(new ${names.ObjectArrayType}(${e.counts[0].genExpr()}, L\"$desc\"))"
		} else {
			"SOBJ(new ${e.type.targetTypeNew}(${e.counts[0].genExpr()}))"
		}
	}

	override fun createArrayMultisure(e: AstExpr.NEW_ARRAY, desc: String): String {
		return "${names.ObjectArrayType}${staticAccessOperator}createMultiSure(L\"$desc\", { ${e.counts.map { it.genExpr() }.joinToString(", ")} } )"
	}

	override fun genExprNew(e: AstExpr.NEW): String {
		return "SOBJ(" + super.genExprNew(e) + ")"
	}

	fun getUnderlyingType(type: AstType): String {
		return when (type) {
			is AstType.ARRAY -> when (type.element) {
				AstType.BOOL -> "JA_Z"
				AstType.BYTE -> "JA_B"
				AstType.CHAR -> "JA_C"
				AstType.SHORT -> "JA_S"
				AstType.INT -> "JA_I"
				AstType.LONG -> "JA_J"
				AstType.FLOAT -> "JA_F"
				AstType.DOUBLE -> "JA_D"
				else -> "JA_L"
			} + "*"
			is AstType.REF -> "${names.getClassFqNameForCalling(type.name)}*"
			else -> type.cppString
		}
	}

	override fun genStmRawTry(trap: AstTrap): Indenter = Indenter.gen {
		//line("try {")
		//_indent()
	}

	override fun genStmRawCatch(trap: AstTrap): Indenter = Indenter.gen {
		//_unindent()
		//line("} catch (SOBJ e) {")
		//indent {
		//	line("if (N::is(e, ${getClassId(trap.exception.name)})) goto ${trap.handler.name};")
		//	line("throw e;")
		//}
		//line("}")
	}

	override fun genStmThrow(stm: AstStm.THROW): Indenter = Indenter.gen {
		//line("""std::wcout << L"THROWING! ${context.clazz}:${context.method.name}" << L"\n";""")
		line(super.genStmThrow(stm))
	}

	override fun genStmRethrow(stm: AstStm.RETHROW): Indenter {
		return super.genStmRethrow(stm)
	}

	override fun genStmLine(stm: AstStm.LINE) = indent {
		mark(stm)
		//line("// ${stm.line}")
	}

	override fun genStmSetArrayLiterals(stm: AstStm.SET_ARRAY_LITERALS) = Indenter.gen {
		val values = stm.values.map { it.genExpr() }

		line("") {
			line("const ${stm.array.type.elementType.cppString} ARRAY_LITERAL[${values.size}] = { ${values.joinToString(", ")} };")
			line("((${stm.array.type.underlyingCppString})((" + stm.array.genExpr() + ").get()))->setArray(${stm.startIndex}, ${values.size}, ARRAY_LITERAL);")
		}
	}

}

val CppKeywords = setOf<String>()

@Singleton
class CppNames(
	program: AstResolver,
	val configMinimizeNames: ConfigMinimizeNames,
	val types: AstTypes
) : CommonNames(program, keywords = CppKeywords) {
	val minimize = configMinimizeNames.minimizeNames
	override val stringPoolType: StringPoolType = StringPoolType.GLOBAL

	override fun buildStaticInit(clazz: AstClass): String = getClassFqNameForCalling(clazz.name) + "::SI();"

	override fun getNativeName(method: MethodRef): String {
		return getClassNameAllocator(method.ref.containingClass).allocate(method.ref) {
			val astMethod = program[method.ref]!!
			val containingClass = astMethod.containingClass

			val prefix = if (containingClass.isInterface) "I_" else if (astMethod.isStatic) "S_" else "M_"
			val prefix2 = if (containingClass.isInterface || method.ref.isClassOrInstanceInit) {
				getClassFqNameForCalling(containingClass.name) + "_"
			} else {
				""
			}

			val suffix = "_" + normalizeName(astMethod.methodType.mangle())

			"$prefix$prefix2" + super.getNativeName(method) + "$suffix"
		}
	}

	override fun buildMethod(method: AstMethod, static: Boolean): String {
		val clazz = getClassFqNameForCalling(method.containingClass.name)
		val name = getNativeName(method)
		return if (static) "$clazz::$name" else name
	}

	override fun buildAccessName(name: String, static: Boolean): String {
		return if (static) "::$name" else "->$name"
	}

	override fun getTypeStringForCpp(type: AstType): String = when (type) {
		AstType.VOID -> "void"
		AstType.BOOL -> "int8_t"
		AstType.BYTE -> "int8_t"
		AstType.CHAR -> "uint16_t"
		AstType.SHORT -> "int16_t"
		AstType.INT -> "int32_t"
		AstType.LONG -> "int64_t"
		AstType.FLOAT -> "float"
		AstType.DOUBLE -> "double"
		is AstType.REF, is AstType.ARRAY -> "SOBJ"
	//is AstType.REF -> "std::shared_ptr<${getClassFqNameForCalling(type.name)}>"
	//is AstType.ARRAY -> "std::shared_ptr<" + when (type.element) {
	//	AstType.BOOL -> "JA_Z"
	//	AstType.BYTE -> "JA_B"
	//	AstType.CHAR -> "JA_C"
	//	AstType.SHORT -> "JA_S"
	//	AstType.INT -> "JA_I"
	//	AstType.LONG -> "JA_J"
	//	AstType.FLOAT -> "JA_F"
	//	AstType.DOUBLE -> "JA_D"
	//	else -> "JA_L"
	//} + ">"
		else -> "AstType_cppString_UNIMPLEMENTED($this)"
	}

	override val staticAccessOperator: String = "::"
	override val instanceAccessOperator: String = "->"

	//override fun N_lnew(value:Long) = "${value}L"

	override fun getNativeName(field: FieldRef): String {
		return getClassNameAllocator(field.ref.containingClass).allocate(field.ref) { "F_" + normalizeName(field.ref.name + "_" + field.ref.type.mangle()) }
	}

	override val NegativeInfinityString = "-INFINITY"
	override val PositiveInfinityString = "INFINITY"
	override val NanString = "NAN"

	override fun escapeConstant(value: Any?): String = when (value) {
		is String -> {
			val id = allocString(currentClass, value)
			//N_func("str", "std::wstring(L" + value.quote() + ", " + value.length + ")")
			"STRINGLIT_$id.get()"
		}
		is AstType -> N_func("resolveClass", "L${value.mangle().uquote()}")
		else -> super.escapeConstant(value)
	}

	override fun buildConstructor(method: AstMethod): String {
		val clazz = getClassFqNameForCalling(method.containingClass.name)
		val methodName = getNativeName(method)
		return "(new $clazz())->$methodName"
	}

	override fun N_lnew(value: Long): String = when (value) {
		Long.MIN_VALUE -> "(int64_t)(0x8000000000000000U)"
		else -> "(int64_t)(${value}L)"
	}
}