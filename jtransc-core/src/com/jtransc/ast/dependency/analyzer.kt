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

package com.jtransc.ast.dependency

import com.jtransc.ast.*
import com.jtransc.error.noImpl

// @TODO: Use generic visitor!
object AstDependencyAnalyzer {
	@JvmStatic fun analyze(program: AstProgram, body: AstBody?, name: String? = null): AstReferences {
		return AstDependencyAnalyzerGen(program, body, name).references
	}

	private class AstDependencyAnalyzerGen(program: AstProgram, body: AstBody?, name: String? = null) {
		val types = hashSetOf<FqName>()
		val fields = hashSetOf<AstFieldRef>()
		val methods = hashSetOf<AstMethodRef>()

		fun ana(type: AstType) = types.addAll(type.getRefTypesFqName())
		fun ana(types: List<AstType>) = types.map { ana(it) }

		fun ana(expr: AstExpr.Box?) = ana(expr?.value)
		fun ana(stm: AstStm.Box?) = ana(stm?.value)

		fun ana(expr: AstExpr?) {
			if (expr == null) return
			when (expr) {
				is AstExpr.CAST -> {
					ana(expr.from)
					ana(expr.to)
					ana(expr.expr)
				}
				is AstExpr.NEW -> {
					ana(expr.target)
				}
				is AstExpr.NEW_ARRAY -> {
					for (c in expr.counts) ana(c)
					ana(expr.arrayType)
				}
				is AstExpr.ARRAY_ACCESS -> {
					ana(expr.type)
					ana(expr.array)
					ana(expr.index)
				}
				is AstExpr.ARRAY_LENGTH -> {
					ana(expr.array)
				}
				is AstExpr.TERNARY -> {
					ana(expr.cond)
					ana(expr.etrue)
					ana(expr.efalse)
				}
				is AstExpr.BINOP -> {
					ana(expr.left)
					ana(expr.right)
				}
				is AstExpr.CALL_BASE -> {
					ana(expr.method.type)
					for (arg in expr.args) ana(arg)
					methods.add(expr.method)
					if (expr is AstExpr.CALL_INSTANCE) ana(expr.obj)
					//if (expr is AstExpr.CALL_SUPER) ana(expr.obj)
				}
				is AstExpr.CAUGHT_EXCEPTION -> {
					ana(expr.type)
				}
				is AstExpr.FIELD_INSTANCE_ACCESS -> {
					ana(expr.expr)
					fields.add(expr.field)
				}
				is AstExpr.FIELD_STATIC_ACCESS -> fields.add(expr.field)
				is AstExpr.INSTANCE_OF -> ana(expr.checkType)
				is AstExpr.UNOP -> ana(expr.right)
				is AstExpr.THIS -> ana(expr.type)
				is AstExpr.LITERAL -> {
					if (expr.value is AstType) {
						types.addAll(expr.value.getRefTypesFqName())
					}

				}
				is AstExpr.LOCAL -> Unit
				is AstExpr.PARAM -> ana(expr.type)
				is AstExpr.METHOD_CLASS -> {
					ana(expr.type)
					methods += expr.methodInInterfaceRef
					methods += expr.methodToConvertRef
					ana(expr.methodInInterfaceRef.allClassRefs)
					ana(expr.methodToConvertRef.allClassRefs)
				}
			//is AstExpr.REF -> ana(expr.expr)
				else -> noImpl("Not implemented $expr")
			}
		}

		fun ana(stm: AstStm?) {
			if (stm == null) return
			when (stm) {
				is AstStm.STMS -> for (s in stm.stms) ana(s)
				is AstStm.STM_EXPR -> ana(stm.expr)
				is AstStm.CONTINUE -> Unit
				is AstStm.BREAK -> Unit
				is AstStm.STM_LABEL -> Unit
				is AstStm.IF_GOTO -> ana(stm.cond)
				is AstStm.GOTO -> Unit
				is AstStm.MONITOR_ENTER -> ana(stm.expr)
				is AstStm.MONITOR_EXIT -> ana(stm.expr)
				is AstStm.SET_LOCAL -> ana(stm.expr)
				is AstStm.SET_ARRAY -> {
					ana(stm.expr); ana(stm.index)
				}
				is AstStm.SET_ARRAY_LITERALS -> {
					ana(stm.array)
					for (v in stm.values) ana(v)
				}
				is AstStm.SET_FIELD_INSTANCE -> {
					fields.add(stm.field); ana(stm.left); ana(stm.expr)
				}
				is AstStm.SET_FIELD_STATIC -> {
					fields.add(stm.field); ana(stm.expr)
				}
				is AstStm.RETURN -> ana(stm.retval)
				is AstStm.RETURN_VOID -> Unit
				is AstStm.IF -> {
					ana(stm.cond); ana(stm.strue);
				}
				is AstStm.IF_ELSE -> {
					ana(stm.cond); ana(stm.strue); ana(stm.sfalse)
				}
				is AstStm.THROW -> ana(stm.value)
				is AstStm.WHILE -> {
					ana(stm.cond); ana(stm.iter)
				}
				is AstStm.TRY_CATCH -> {
					ana(stm.trystm);
					ana(stm.catch)
					/*
					for (catch in stm.catches) {
						ana(catch.first)
						ana(catch.second)
					}
					*/
				}
				is AstStm.SWITCH_GOTO -> {
					ana(stm.subject)
				}
				is AstStm.SWITCH -> {
					ana(stm.subject); ana(stm.default)
					for (catch in stm.cases) ana(catch.second)
				}
				is AstStm.SET_NEW_WITH_CONSTRUCTOR -> {
					ana(stm.target)
					ana(stm.method.type)
					for (arg in stm.args) {
						ana(arg)
					}
				}
				is AstStm.LINE -> Unit
				is AstStm.NOP -> Unit
				else -> throw NotImplementedError("Not implemented STM $stm")
			}
		}

		init {
			if (body != null) {
				for (local in body.locals) ana(local.type)

				ana(body.stm)

				for (trap in body.traps) {
					types.add(trap.exception.name)
				}
			}
		}

		val references = AstReferences(
			program = program,
			classes = types.map { AstType.REF(it) }.toSet(),
			fields = fields.toSet(),
			methods = methods.toSet()
		)
	}
}
