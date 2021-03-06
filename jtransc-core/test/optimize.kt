import com.jtransc.ast.*
import com.jtransc.ast.optimize.optimize
import com.jtransc.types.dump
import com.jtransc.types.exprDump
import org.junit.Assert
import org.junit.Test

class OptimizeTest {
	val types = AstTypes()
	val flags = AstBodyFlags(strictfp = false, types = types)

	@Test fun test1() {
		val expr = AstBuild(types) { null.lit.cast(OBJECT).cast(CLASS).cast(STRING) }
		Assert.assertEquals("((java.lang.String)((java.lang.Class)((java.lang.Object)null)))", expr.exprDump())
		expr.optimize(flags)
		Assert.assertEquals("((java.lang.String)null)", expr.exprDump())
	}

	@Test fun test2() {
		Assert.assertEquals("1", AstBuild(types) { 1.lit.cast(INT) }.optimize(flags).exprDump())
		Assert.assertEquals("(Class1.array).length", AstExpr.ARRAY_LENGTH(
			AstExpr.FIELD_STATIC_ACCESS(AstFieldRef("Class1".fqname, "array", AstType.ARRAY(AstType.INT)))
		).optimize(flags).exprDump())
	}

	@Test fun test3() {
		Assert.assertEquals("test", AstExpr.BINOP(AstType.BOOL, AstExpr.CAST(AstExpr.LOCAL(AstLocal(0, "test", AstType.BOOL)), AstType.INT), AstBinop.EQ, AstExpr.LITERAL(1, types)).optimize(flags).exprDump())
		Assert.assertEquals("test", AstExpr.BINOP(AstType.BOOL, AstExpr.CAST(AstExpr.LOCAL(AstLocal(0, "test", AstType.BOOL)), AstType.INT), AstBinop.NE, AstExpr.LITERAL(0, types)).optimize(flags).exprDump())

		Assert.assertEquals("(!test)", AstExpr.BINOP(AstType.BOOL, AstExpr.CAST(AstExpr.LOCAL(AstLocal(0, "test", AstType.BOOL)), AstType.INT), AstBinop.EQ, AstExpr.LITERAL(0, types)).optimize(flags).exprDump())
		Assert.assertEquals("(!test)", AstExpr.BINOP(AstType.BOOL, AstExpr.CAST(AstExpr.LOCAL(AstLocal(0, "test", AstType.BOOL)), AstType.INT), AstBinop.NE, AstExpr.LITERAL(1, types)).optimize(flags).exprDump())
	}

	@Test fun test4() {
		Assert.assertEquals("true", AstExpr.BINOP(AstType.BOOL, AstExpr.CAST(AstExpr.LITERAL(true, types), AstType.INT), AstBinop.EQ, AstExpr.LITERAL(1, types)).optimize(flags).exprDump())
		Assert.assertEquals("false", AstBuild(types) { 0.lit.cast(BOOL) }.optimize(flags).exprDump())
		Assert.assertEquals("true", AstBuild(types) { 1.lit.cast(BOOL) }.optimize(flags).exprDump())
	}

	@Test fun test5() {
		val test = AstExpr.LOCAL(AstLocal(0, "test", AstType.INT))
		Assert.assertEquals("(test != 10)", AstBuild(types) { test ne 10.lit }.optimize(flags).exprDump())
		Assert.assertEquals("(test == 10)", AstBuild(types) { (test ne 10.lit).not() }.optimize(flags).exprDump())
		Assert.assertEquals("(test != 10)", AstBuild(types) { (test ne 10.lit).not().not() }.optimize(flags).exprDump())
	}
}