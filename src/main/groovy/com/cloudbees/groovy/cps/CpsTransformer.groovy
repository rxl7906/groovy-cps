package com.cloudbees.groovy.cps

import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ArrayExpression
import org.codehaus.groovy.ast.expr.AttributeExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.BitwiseNegationExpression
import org.codehaus.groovy.ast.expr.BooleanExpression
import org.codehaus.groovy.ast.expr.CastExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ClosureListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression
import org.codehaus.groovy.ast.expr.FieldExpression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.MethodPointerExpression
import org.codehaus.groovy.ast.expr.NotExpression
import org.codehaus.groovy.ast.expr.PostfixExpression
import org.codehaus.groovy.ast.expr.PrefixExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.RangeExpression
import org.codehaus.groovy.ast.expr.SpreadExpression
import org.codehaus.groovy.ast.expr.SpreadMapExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.expr.TernaryExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.UnaryMinusExpression
import org.codehaus.groovy.ast.expr.UnaryPlusExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.AssertStatement
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.BreakStatement
import org.codehaus.groovy.ast.stmt.CaseStatement
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.stmt.ContinueStatement
import org.codehaus.groovy.ast.stmt.DoWhileStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.SwitchStatement
import org.codehaus.groovy.ast.stmt.SynchronizedStatement
import org.codehaus.groovy.ast.stmt.ThrowStatement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.ast.stmt.WhileStatement
import org.codehaus.groovy.classgen.BytecodeExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class CpsTransformer extends CompilationCustomizer implements GroovyCodeVisitor {
    CpsTransformer() {
        super(CompilePhase.CANONICALIZATION)
    }

    @Override
    void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        def ast = source.getAST();

        ast.methods?.each { visitMethod(it) }
        classNode?.declaredConstructors?.each { visitMethod(it) }
        classNode?.methods?.each { visitMethod(it) }
//        classNode?.objectInitializerStatements?.each { it.visit(visitor) }
//        classNode?.fields?.each { visitor.visitField(it) }
    }

    /**
     * Should this method be transformed?
     */
    private boolean shouldBeTransformed(MethodNode node) {
        return node.annotations.find { it.classNode.name==WorkflowMethod.class.name } != null;
    }

    public void visitMethod(MethodNode node) {
        if (!shouldBeTransformed(node))
            return;

        // function shall now return the Function object
        node.returnType = FUNCTION_TYPE;

        node.code.visit(this)
    }

    /**
     * As we visit expressions in the method body, we convert them to the {@link Builder} invocations
     * and pass them back to this closure.
     */
    private Closure parent;

    private void visit(ASTNode e) {
        e.visit(this);
    }

    private void visit(Collection<? extends ASTNode> col) {
        for (def e : col) {
            e.visit(this);
        }
    }

    /**
     * Makes an AST fragment that calls {@link Builder} with specific method.
     *
     * @param args
     *      Can be closure for building argument nodes, Expression, or List of Expressions.
     */
    private MethodCallExpression makeNode(String methodName, Object args) {
        if (args instanceof Closure) {
            def argExps = []
            def old = parent;
            try {
                parent = { a -> argExps.addExpression(a) }

                args(); // evaluate arguments
                args = argExps;
            } finally {
                parent = old
            }
        }

        parent(new MethodCallExpression(BUILDER, methodName, new TupleExpression(args)));
    }

    void visitMethodCallExpression(MethodCallExpression call) {
        makeNode("functionCall") {
            visit(call.objectExpression);
            // TODO: spread & safe
            visit(call.method);
            visit(((TupleExpression)call.arguments).expressions)
        }
    }

    void visitBlockStatement(BlockStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitForLoop(ForStatement forLoop) {
        throw new UnsupportedOperationException();
    }

    void visitWhileLoop(WhileStatement loop) {
        throw new UnsupportedOperationException();
    }

    void visitDoWhileLoop(DoWhileStatement loop) {
        throw new UnsupportedOperationException();
    }

    void visitIfElse(IfStatement ifElse) {
        throw new UnsupportedOperationException();
    }

    void visitExpressionStatement(ExpressionStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitReturnStatement(ReturnStatement statement) {
        makeNode("return_") {
            visit(statement.expression);
        }
    }

    void visitAssertStatement(AssertStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitTryCatchFinally(TryCatchStatement finally1) {
        throw new UnsupportedOperationException();
    }

    void visitSwitch(SwitchStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitCaseStatement(CaseStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitBreakStatement(BreakStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitContinueStatement(ContinueStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitThrowStatement(ThrowStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitSynchronizedStatement(SynchronizedStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitCatchStatement(CatchStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitStaticMethodCallExpression(StaticMethodCallExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitConstructorCallExpression(ConstructorCallExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitTernaryExpression(TernaryExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitShortTernaryExpression(ElvisOperatorExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitBinaryExpression(BinaryExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitPrefixExpression(PrefixExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitPostfixExpression(PostfixExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitBooleanExpression(BooleanExpression expression) {
        visit(expression);
    }

    void visitClosureExpression(ClosureExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitTupleExpression(TupleExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitMapExpression(MapExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitMapEntryExpression(MapEntryExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitListExpression(ListExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitRangeExpression(RangeExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitPropertyExpression(PropertyExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitAttributeExpression(AttributeExpression attributeExpression) {
        throw new UnsupportedOperationException();
    }

    void visitFieldExpression(FieldExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitMethodPointerExpression(MethodPointerExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitConstantExpression(ConstantExpression expression) {
        makeNode("constant", expression)
    }

    void visitClassExpression(ClassExpression expression) {
        makeNode("constant", expression)
    }

    void visitVariableExpression(VariableExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitDeclarationExpression(DeclarationExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitGStringExpression(GStringExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitArrayExpression(ArrayExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitSpreadExpression(SpreadExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitSpreadMapExpression(SpreadMapExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitNotExpression(NotExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitUnaryMinusExpression(UnaryMinusExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitUnaryPlusExpression(UnaryPlusExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitCastExpression(CastExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitArgumentlistExpression(ArgumentListExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitClosureListExpression(ClosureListExpression closureListExpression) {
        throw new UnsupportedOperationException();
    }

    void visitBytecodeExpression(BytecodeExpression expression) {
        throw new UnsupportedOperationException();
    }

    private static final ClassNode FUNCTION_TYPE = ClassHelper.makeCached(Function.class);
    private static final ClassNode BUILDER_TYPE = ClassHelper.makeCached(Builder.class);
    private static final PropertyExpression BUILDER = new PropertyExpression(new ClassExpression(BUILDER_TYPE), "INSTANCE")
}