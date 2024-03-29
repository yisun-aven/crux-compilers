package crux.ast;

import com.sun.source.tree.IdentifierTree;
import crux.ast.*;
import crux.ast.OpExpr.Operation;
import crux.ir.insts.CallInst;
import crux.pt.CruxBaseVisitor;
import crux.pt.CruxParser;
import crux.ast.types.*;
import crux.ast.SymbolTable.Symbol;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class will convert the parse tree generated by ANTLR to AST It follows the visitor pattern
 * where declarations will be by DeclarationVisitor Class Statements will be resolved by
 * StatementVisitor Class Expressions will be resolved by ExpressionVisitor Class
 */

public final class ParseTreeLower {
  private final DeclarationVisitor declarationVisitor = new DeclarationVisitor();
  private final StatementVisitor statementVisitor = new StatementVisitor();
  private final ExpressionVisitor expressionVisitor = new ExpressionVisitor();

  private final SymbolTable symTab;

  public ParseTreeLower(PrintStream err) {
    symTab = new SymbolTable(err);
  }

  private static Position makePosition(ParserRuleContext ctx) {
    var start = ctx.start;
    return new Position(start.getLine());
  }

  /**helper function to chekc the type*/
  private static Type TypeCheckFunc(CruxParser.FunctionDefinitionContext ctx) {
    Type type;
    if(ctx.type().getText().equals("int"))
    {
      type = new IntType();
      return type;
    }else if(ctx.type().getText().equals("bool"))
    {
      type = new BoolType();
      return type;
    }else if(ctx.type().getText().equals("void"))
    {
      type = new VoidType();
      return type;
    }
    return null;
  }

  private static Type TypeCheckParam(CruxParser.ParameterContext ctx) {
    Type type;
    if(ctx.type().getText().equals("int"))
    {
      type = new IntType();
      return type;
    }else if(ctx.type().getText().equals("bool"))
    {
      type = new BoolType();
      return type;
    }else if(ctx.type().getText().equals("void"))
    {
      type = new VoidType();
      return type;
    }
    return null;
  }


  private static Type TypeCheckArray(CruxParser.ArrayDeclarationContext ctx) {
    Type type;
    if(ctx.type().getText().equals("int"))
    {
      type = new IntType();
      return type;
    }else if(ctx.type().getText().equals("bool"))
    {
      type = new BoolType();
      return type;
    }else if(ctx.type().getText().equals("void"))
    {
      type = new VoidType();
      return type;
    }
    return null;
  }

  private static Type TypeCheckVariableDeclaration(CruxParser.VariableDeclarationContext ctx) {
    Type type;
    if(ctx.type().getText().equals("int"))
    {
      type = new IntType();
      return type;
    }else if(ctx.type().getText().equals("bool"))
    {
      type = new BoolType();
      return type;
    }else if(ctx.type().getText().equals("void"))
    {
      type = new VoidType();
      return type;
    }
    return null;
  }



  /**helper function to check the operation of op0*/
  private static Operation op0Check(CruxParser.Expression0Context ctx)
  {
    CruxParser.Op0Context op0 = ctx.op0();
    Operation op;
    if(op0.GREATER_EQUAL() != null)
    {
      op = Operation.GE;
      return op;
    }else if(op0.LESSER_EQUAL() != null)
    {
      op = Operation.LE;
      return op;
    }else if(op0.NOT_EQUAL() != null)
    {
      op = Operation.NE;
      return op;
    }else if(op0.EQUAL() != null)
    {
      op = Operation.EQ;
      return op;
    }
    else if(op0.GREATER_THAN() != null)
    {
      op = Operation.GT;
      return op;
    }else if(op0.LESS_THAN() != null)
    {
      op = Operation.LT;
      return op;
    }
    return null;
  }

  /**helper function to check the operation of op1*/
  private static Operation op1Check(CruxParser.Expression1Context ctx)
  {
    CruxParser.Op1Context op1 = ctx.op1();
    Operation op;
    if(op1.ADD() != null)
    {
      op = Operation.ADD;
      return op;
    }else if(op1.SUB() != null)
    {
      op = Operation.SUB;
      return op;
    }else if(op1.OR() != null)
    {
      op = Operation.LOGIC_OR;
      return op;
    }
    return null;
  }

  /**helper function to check the operation of op2*/
  private static Operation op2Check(CruxParser.Expression2Context ctx)
  {
    CruxParser.Op2Context op2 = ctx.op2();
    Operation op;
    if(op2.MUL() != null)
    {
      op = Operation.MULT;
      return op;
    }else if(op2.DIV() != null)
    {
      op = Operation.DIV;
      return op;
    }else if(op2.AND() != null)
    {
      op = Operation.LOGIC_AND;
      return op;
    }
    return null;
  }

  /**
   *
   * @return True if any errors
   */
  public boolean hasEncounteredError() {
    return symTab.hasEncounteredError();
  }


  /**
   * Lower top-level parse tree to AST
   *
   * @return a {@link DeclarationList} object representing the top-level AST.
   */

  public DeclarationList lower(CruxParser.ProgramContext program) {
    ArrayList<Declaration> list = new ArrayList<Declaration>();
    for(CruxParser.DeclarationContext context: program.declarationList().declaration())
    {
      Declaration node = context.accept(declarationVisitor);
      list.add(node);
    }
    return new DeclarationList(makePosition(program.declarationList()), list);
  }

  /**
   * Lower statement list by lower individual statement into AST.
   *
   * @return a {@link StatementList} AST object.
   */


  private StatementList lower(CruxParser.StatementListContext statementList) {
    ArrayList<Statement> list = new ArrayList<Statement> ();
    System.out.println("Thanks");
    for(CruxParser.StatementContext context: statementList.statement()) {
      Statement node = context.accept(statementVisitor);
      list.add(node);
    }

    return new StatementList(makePosition(statementList), list);
  }


  /**
   * Similar to {@link #lower(CruxParser.StatementListContext)}, but handles symbol table as well.
   *
   * @return a {@link StatementList} AST object.
   */


  private StatementList lower(CruxParser.StatementBlockContext statementBlock)
  {
    symTab.enter();
    StatementList sl = lower(statementBlock.statementList());
    symTab.exit();
    return sl;
  }


  /**
   * A parse tree visitor to create AST nodes derived from {@link Declaration}
   */
  private final class DeclarationVisitor extends CruxBaseVisitor<Declaration> {
    /**
     * Visit a parse tree variable declaration and create an AST {@link VariableDeclaration}
     *
     * @return an AST {@link VariableDeclaration}
     */



    @Override
    public VariableDeclaration visitVariableDeclaration(CruxParser.VariableDeclarationContext ctx) {
      String name = ctx.Identifier().getText();
      Type type = TypeCheckVariableDeclaration(ctx);
      Symbol symbol = symTab.add(makePosition(ctx), name, type);
      return new VariableDeclaration(makePosition(ctx), symbol);
    }




    /**
     * Visit a parse tree array declaration and creates an AST {@link ArrayDeclaration}
     *
     * @return an AST {@link ArrayDeclaration}
     */


    //Modify Later
    @Override
    public Declaration visitArrayDeclaration(CruxParser.ArrayDeclarationContext ctx) {
      String name = ctx.Identifier().getText();
      Type baseType = TypeCheckArray(ctx);
      long extent = Long.parseLong(ctx.Integer().getText());
      Type type = new ArrayType(extent, baseType);
      Symbol symbol = symTab.add(makePosition(ctx), name, type);
      return new ArrayDeclaration(makePosition(ctx), symbol);
    }


    /**
     * Visit a parse tree function definition and create an AST {@link FunctionDefinition}
     *
     * @return an AST {@link FunctionDefinition}
     */


    /** This one ask TA how to implement*/
    @Override
    public Declaration visitFunctionDefinition(CruxParser.FunctionDefinitionContext ctx) {
      String s = ctx.Identifier().getText();

      Type returnType = TypeCheckFunc(ctx);
      TypeList args = new TypeList();
      for (CruxParser.ParameterContext param : ctx.parameterList().parameter()) {
        args.append(TypeCheckParam(param));
      }

      FuncType funcType = new FuncType(args, returnType);
      Symbol symbol1 = symTab.add(makePosition(ctx), s, funcType);

      symTab.enter();
      List<Symbol> parameterList = new ArrayList<Symbol>();
      for(CruxParser.ParameterContext parameterContext : ctx.parameterList().parameter())
      {
        String paramName = parameterContext.Identifier().getText();
        Type paramType = TypeCheckParam(parameterContext);
        Symbol symbol = symTab.add(makePosition(parameterContext), paramName, paramType);
        parameterList.add(symbol);
      }

      //System.out.println("Test");
      StatementList statementList = lower(ctx.statementBlock().statementList());
      System.out.println("Test");
      symTab.exit();
      System.out.println("Test4");
      return new FunctionDefinition(makePosition(ctx), symbol1, parameterList, statementList);

    }

  }


  /**
   * A parse tree visitor to create AST nodes derived from {@link Statement}
   */

  private final class StatementVisitor extends CruxBaseVisitor<Statement> {
    /**
     * Visit a parse tree variable declaration and create an AST {@link VariableDeclaration}. Since
     * {@link VariableDeclaration} is both {@link Declaration} and {@link Statement}, we simply
     * delegate this to
     * {@link DeclarationVisitor#visitArrayDeclaration(CruxParser.ArrayDeclarationContext)} which we
     * implement earlier.
     *
     * @return an AST {@link VariableDeclaration}
     */


    @Override
    public Statement visitVariableDeclaration(CruxParser.VariableDeclarationContext ctx) {
      return declarationVisitor.visitVariableDeclaration(ctx);
    }


    /**
     * Visit a parse tree assignment statement and create an AST {@link Assignment}
     *
     * @return an AST {@link Assignment}
     */


    @Override
    public Statement visitAssignmentStatement(CruxParser.AssignmentStatementContext ctx) {
      return new Assignment(makePosition(ctx), ctx.designator().accept(expressionVisitor), ctx.expression0().accept(expressionVisitor));
    }


    /**
     * Visit a parse tree assignment nosemi statement and create an AST {@link Assignment}
     *
     * @return an AST {@link Assignment}
     */


    @Override
    public Statement visitAssignmentStatementNoSemi(CruxParser.AssignmentStatementNoSemiContext ctx) {
      return new Assignment(makePosition(ctx), ctx.designator().accept(expressionVisitor), ctx.expression0().accept(expressionVisitor));
    }


    /**
     * Visit a parse tree call statement and create an AST {@link Call}. Since {@link Call} is both
     * {@link Expression} and {@link Statement}, we simply delegate this to
     * {@link ExpressionVisitor#visitCallExpression(CruxParser.CallExpressionContext)} that we will
     * implement later.
     *
     * @return an AST {@link Call}
     */


    @Override
    public Statement visitCallStatement(CruxParser.CallStatementContext ctx) {
      return expressionVisitor.visitCallExpression(ctx.callExpression());
    }


    /**
     * Visit a parse tree if-else branch and create an AST {@link IfElseBranch}. The template code
     * shows partial implementations that visit the then block and else block recursively before
     * using those returned AST nodes to construct {@link IfElseBranch} object.
     *
     * @return an AST {@link IfElseBranch}
     */


    //Modify later
    @Override
    public Statement visitIfStatement(CruxParser.IfStatementContext ctx) {
      Expression condition = ctx.expression0().accept(expressionVisitor);
      StatementList thenBlockStatementList = lower(ctx.statementBlock(0));

      StatementList elseBlockStatementList;
      if(ctx.ELSE() == null)
      {
        elseBlockStatementList = new StatementList(makePosition(ctx), new ArrayList<Statement>());
      }
      else {
        elseBlockStatementList = lower(ctx.statementBlock(1));
      }
      return new IfElseBranch(makePosition(ctx), condition, thenBlockStatementList, elseBlockStatementList);
    }

    /**
     * Visit a parse tree for loop and create an AST {@link For}. You'll going to use a similar
     * techniques as {@link #visitIfStatement(CruxParser.IfStatementContext)} to decompose this
     * construction.
     *
     * @return an AST {@link For}
     */


    @Override
    public Statement visitForStatement(CruxParser.ForStatementContext ctx) {

      Assignment assignmentStatement1 =
              new Assignment(makePosition(ctx), ctx.assignmentStatement().designator().accept(expressionVisitor), ctx.assignmentStatement().expression0().accept(expressionVisitor));
      Expression condition = ctx.expression0().accept(expressionVisitor);
      Assignment assignmentStatementNoSemi =
              new Assignment(makePosition(ctx), ctx.assignmentStatementNoSemi().designator().accept(expressionVisitor), ctx.assignmentStatementNoSemi().expression0().accept(expressionVisitor));
      StatementList body = lower(ctx.statementBlock());
      return new For(makePosition(ctx), assignmentStatement1, condition, assignmentStatementNoSemi, body);
    }

    /**
     * Visit a parse tree return statement and create an AST {@link Return}. Here we show a simple
     * example of how to lower a simple parse tree construction.
     *
     * @return an AST {@link Return}
     */

    @Override
    public Statement visitReturnStatement(CruxParser.ReturnStatementContext ctx) {
      Expression returnExpression = ctx.expression0().accept(expressionVisitor);
      return new Return(makePosition(ctx), returnExpression);
    }

    /**
     * Creates a Break node
     */

    @Override
    public Statement visitBreakStatement(CruxParser.BreakStatementContext ctx) {
      return new Break(makePosition(ctx));
    }
  }

  private final class ExpressionVisitor extends CruxBaseVisitor<Expression> {
    /**
     * Parse Expression0 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammer
     */

    /** ask ta */
    @Override
    public Expression visitExpression0(CruxParser.Expression0Context ctx) {
      if(ctx.op0() == null) {
        System.out.println("Test0");
        return ctx.expression1(0).accept(expressionVisitor);
      }else
      {
        //System.out.println("Test1");
        Expression lhs = ctx.expression1(0).accept(expressionVisitor);
        Expression rhs = ctx.expression1(1).accept(expressionVisitor);
        /** check with TA about the index of op0*/
        return new OpExpr(makePosition(ctx), op0Check(ctx), lhs, rhs);
      }

    }

    /**
     * Parse Expression1 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammer
     */


    @Override
    public Expression visitExpression1(CruxParser.Expression1Context ctx) {
      if(ctx.op1() == null) {
        System.out.println("Test3");
        return ctx.expression2().accept(expressionVisitor);
      } else
      {
        Expression lhs = ctx.expression1().accept(expressionVisitor);
        Expression rhs = ctx.expression2().accept(expressionVisitor);
        return new OpExpr(makePosition(ctx), op1Check(ctx), lhs, rhs);
      }
    }


    /**
     * Parse Expression2 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammer
     */


    @Override
    public Expression visitExpression2(CruxParser.Expression2Context ctx) {
      if(ctx.op2() == null) {
        System.out.println("Test2");
        return ctx.expression3().accept(expressionVisitor);
      } else
      {
        Expression lhs = ctx.expression2().accept(expressionVisitor);
        Expression rhs = ctx.expression3().accept(expressionVisitor);
        return new OpExpr(makePosition(ctx), op2Check(ctx), lhs, rhs);
      }
    }

    /**
     * Parse Expression3 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammer
     */

    @Override
    public Expression visitExpression3(CruxParser.Expression3Context ctx) {
      if(ctx.NOT() != null)
      {
        //System.out.println("Test4");
        Expression exp = ctx.expression3().accept(expressionVisitor);
        return new OpExpr(makePosition(ctx), Operation.LOGIC_NOT, exp, null);
      }else if(ctx.designator() != null)
      {
        return ctx.designator().accept(expressionVisitor);
      }else if(ctx.literal() != null)
      {
        return ctx.literal().accept(expressionVisitor);
      }else if(ctx.expression0() != null)
      {
        System.out.println("Test1");
        return ctx.expression0().accept(expressionVisitor);
      }
      else if(ctx.callExpression() != null)
      {
        return ctx.callExpression().accept(expressionVisitor);
      }
      else
      {
        return null;
      }
    }


    /**
     * Create an Call Node
     */

    @Override
    public Call visitCallExpression(CruxParser.CallExpressionContext ctx) {
      String name = ctx.Identifier().getText();
      Symbol symbol = symTab.lookup(makePosition(ctx), name);
      CruxParser.ExpressionListContext eContext = ctx.expressionList();
      return new Call(makePosition(ctx), symbol, expressionList(eContext));
    }

    /** helper function for expressionList
     *
     */
    private List<Expression> expressionList(CruxParser.ExpressionListContext ctx)
    {
      List<Expression> le = new ArrayList<Expression>();
      for(CruxParser.Expression0Context context: ctx.expression0())
      {
        Expression expression = context.accept(expressionVisitor);
        le.add(expression);
      }

      return le;
    }


    /**
     * visitDesignator will check for a name or ArrayAccess FYI it should account for the case when
     * the designator was dereferenced
     */

    @Override
    public Expression visitDesignator(CruxParser.DesignatorContext ctx) {
      String name = ctx.Identifier().getText();
      Symbol symbol = symTab.lookup(makePosition(ctx), name);

      if (ctx.expression0().size() == 0){
        return new VarAccess(makePosition(ctx), symbol);
      } else if (ctx.expression0().size() == 1){
        Expression expression = expressionVisitor.visitExpression0(ctx.expression0(0));
        return new ArrayAccess(makePosition(ctx.expression0(0)), symbol, expression);
      }
      return null;
    }


    /**
     * Create an Literal Node
     */

    //Modify later
    @Override
    public Expression visitLiteral(CruxParser.LiteralContext ctx) {
      //String number = ctx.getText();
      //Integer i = new Integer(number);
      //return new LiteralInt(makePosition(ctx), i)
      if (ctx.Integer() != null) {
        return new LiteralInt(makePosition(ctx), Long.parseLong(ctx.Integer().getText()));
      } else if (ctx.True() != null) {
        return new LiteralBool(makePosition(ctx), Boolean.parseBoolean(ctx.True().getText()));
      } else if (ctx.False() != null) {
        return new LiteralBool(makePosition(ctx), Boolean.parseBoolean(ctx.False().getText()));
      } else {
        return null;
      }
    }
  }
}
