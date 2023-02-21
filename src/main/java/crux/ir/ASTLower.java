package crux.ir;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.OpExpr.Operation;
import crux.ast.traversal.NodeVisitor;
import crux.ast.types.*;
import crux.ir.insts.*;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class InstPair {
  private Instruction start;
  private Instruction end;
  private LocalVar value;

  public InstPair(Instruction s, Instruction e, LocalVar v)
  {
    start = s;
    end = e;
    value = v;
  }

  public InstPair(Instruction b, LocalVar v)
  {
    start = b;
    end = b;
    value = v;
  }

  public InstPair(Instruction s, Instruction e)
  {
    start = s;
    end = e;
    value = null;
  }

  public InstPair(Instruction b)
  {
    start = b;
    end = b;
    value = null;
  }

  public void set_edge(Instruction i)
  {
    end.setNext(0, i);
  }

  public Instruction get_start()
  {
    return start;
  }

  public Instruction get_end() { return end; }

  public LocalVar get_localVal()
  {
    return value;
  }

}


/**
 * Convert AST to IR and build the CFG
 */
public final class ASTLower implements NodeVisitor<InstPair> {
  private Program mCurrentProgram = null;
  private Function mCurrentFunction = null;

  private Map<Symbol, LocalVar> mCurrentLocalVarMap = null;

  Stack<Instruction> instructionStack = new Stack<Instruction>();;

  /**
   * A constructor to initialize member variables
   */
  public ASTLower() {}

  public Program lower(DeclarationList ast) {
    visit(ast);
    return mCurrentProgram;
  }

  @Override
  public InstPair visit(DeclarationList declarationList) {
    mCurrentProgram = new Program();
    //mCurrentLocalVarMap = new HashMap<>();
    for(var child: declarationList.getChildren())
    {
      child.accept(this);
    }

    return null;
  }

  /**
   * This visitor should create a Function instance for the functionDefinition node, add parameters
   * to the localVarMap, add the function to the program, and init the function start Instruction.
   */
  @Override
  public InstPair visit(FunctionDefinition functionDefinition) {
    mCurrentFunction = new Function(functionDefinition.getSymbol().getName(), (FuncType) functionDefinition.getSymbol().getType());
    mCurrentLocalVarMap = new HashMap<Symbol, LocalVar>();
    List<LocalVar> args = new ArrayList<>();
    for(Symbol arg: functionDefinition.getParameters())
    {//create temp var and map symbol
      LocalVar var = mCurrentFunction.getTempVar(arg.getType(), arg.getName()); //new LocalVar(arg.getType(), arg.getName());
      mCurrentLocalVarMap.put(arg, var);
      args.add(var);
    }

    //visit func and set the instruction of mcf
    mCurrentFunction.setArguments(args);
    mCurrentProgram.addFunction(mCurrentFunction);
    mCurrentFunction.setStart(functionDefinition.getStatements().accept(this).get_start());
    mCurrentFunction = null;
    mCurrentLocalVarMap = null;

    return null;
  }

  @Override
  public InstPair visit(StatementList statementList) {
    NopInst start = new NopInst();
    Instruction end = start;
    if(statementList.getChildren().size() == 0)
    {
      return new InstPair(start);
    }
    for(Node node: statementList.getChildren())
    {
      InstPair instPair = node.accept(this);
      end.setNext(0, instPair.get_start());
      end = instPair.get_end();
    }
    return new InstPair(start, end);
  }

  /**
   * Declarations, could be either local or Global
   */
  @Override
  public InstPair visit(VariableDeclaration variableDeclaration) {
    if(mCurrentFunction == null)
    {
      mCurrentProgram.addGlobalVar(new GlobalDecl(variableDeclaration.getSymbol(), IntegerConstant.get(mCurrentProgram, 1)));
    }
    else
    {
      LocalVar var = mCurrentFunction.getTempVar(variableDeclaration.getSymbol().getType());
      mCurrentLocalVarMap.put(variableDeclaration.getSymbol(), var);

      return new InstPair(new NopInst());
    }

    return null;
  }

  /**
   * Create a declaration for array and connected it to the CFG
   */
  @Override
  public InstPair visit(ArrayDeclaration arrayDeclaration) {
    ArrayType at = (ArrayType) arrayDeclaration.getSymbol().getType();
    mCurrentProgram.addGlobalVar(new GlobalDecl(arrayDeclaration.getSymbol(), IntegerConstant.get(mCurrentProgram, at.getExtent())));

    return null;
  }

  /**
   * LookUp the name in the map(s). For globals, we should do a load to get the value to load into a
   * LocalVar.
   */
  @Override
  public InstPair visit(VarAccess name) {
    if(mCurrentLocalVarMap.get(name.getSymbol()) == null)
    {
      AddressVar av = mCurrentFunction.getTempAddressVar(name.getSymbol().getType());
      AddressAt aa = new AddressAt(av, name.getSymbol());
      LocalVar lv = mCurrentFunction.getTempVar(name.getType());
      LoadInst li = new LoadInst(lv, av);
      aa.setNext(0, li);
      return new InstPair(aa, li, lv);
    }
    else
    {

      return new InstPair(new NopInst(), mCurrentLocalVarMap.get(name.getSymbol()));
    }
  }

  /**
   * If the location is a VarAccess to a LocalVar, copy the value to it. If the location is a
   * VarAccess to a global, store the value. If the location is ArrayAccess, store the value.
   */
  @Override
  public InstPair visit(Assignment assignment) {
    if(assignment.getLocation() instanceof VarAccess){
      if(mCurrentLocalVarMap.get(((VarAccess) assignment.getLocation()).getSymbol()) == null) //global
      {
        AddressVar av = mCurrentFunction.getTempAddressVar(((VarAccess) assignment.getLocation()).getType());
        AddressAt aa = new AddressAt(av, ((VarAccess) assignment.getLocation()).getSymbol());
        InstPair rhs = assignment.getValue().accept(this);
        StoreInst si = new StoreInst(rhs.get_localVal(), av);
        aa.setNext(0,rhs.get_start());
        rhs.set_edge(si);

        return new InstPair(aa, si);
      }
      else //local
      {
        InstPair rhs = assignment.getValue().accept(this);
        CopyInst ci = new CopyInst(mCurrentLocalVarMap.get(((VarAccess) assignment.getLocation()).getSymbol()), rhs.get_localVal());
        rhs.set_edge(ci);

        return new InstPair(rhs.get_start(), ci);
      }

    }
    else
    {
      InstPair arrayi = ((ArrayAccess) assignment.getLocation()).getIndex().accept(this);
      InstPair rhs = assignment.getValue().accept(this);
      AddressVar av = mCurrentFunction.getTempAddressVar(((ArrayAccess) assignment.getLocation()).getType());
      AddressAt aa = new AddressAt(av, ((ArrayAccess) assignment.getLocation()).getBase(), arrayi.get_localVal());
      StoreInst si = new StoreInst(rhs.get_localVal(), av);

      arrayi.set_edge(aa);
      aa.setNext(0,rhs.get_start());
      rhs.set_edge(si);

      return new InstPair(arrayi.get_start(), si);
    }
  }

  /**
   * Lower a Call.
   */
  @Override
  public InstPair visit(Call call) {
    List<LocalVar> params = new ArrayList<>();
    NopInst firstNop = new NopInst();
    Instruction start = firstNop;
    Instruction end = firstNop;
    int i = 1;

    for(Expression arg: call.getArguments())
    {
      InstPair instPair = arg.accept(this);

      if(i == 1)
      {
        firstNop.setNext(0, instPair.get_start());
        start = instPair.get_start();
        end = instPair.get_end();
        i--;
      }
      else
      {
        end.setNext(0, instPair.get_start());
        end = instPair.get_end();
      }

      params.add(instPair.get_localVal());
    }

    InstPair newInstPair = new InstPair(start, end);

    CallInst ci;


    FuncType ft = (FuncType) call.getCallee().getType();
    if(ft.getRet() instanceof VoidType)
    {
      ci = new CallInst(call.getCallee(), params);
      newInstPair.set_edge(ci);

      return new InstPair(start, ci);
    }
    else
    {
      LocalVar lv = mCurrentFunction.getTempVar(call.getCallee().getType());
      ci = new CallInst(lv, call.getCallee(), params);
      newInstPair.set_edge(ci);

      return new InstPair(start, ci, lv);

    }


  }

  /**
   * Handle operations like arithmetics and comparisons. Also handle logical operations (and,
   * or, not).
   */
  @Override
  public InstPair visit(OpExpr operation) {
    System.out.println("OpExpr is used");
    InstPair lhs = operation.getLeft().accept(this);
    //UnaryNot Operator
    if(operation.getRight() == null)
    {
      System.out.println("It is !=");
      var destVar = mCurrentFunction.getTempVar(operation.getType());
      UnaryNotInst unaryNotInst = new UnaryNotInst(destVar, lhs.get_localVal());
      lhs.set_edge(unaryNotInst);
      return new InstPair(lhs.get_start(), unaryNotInst, destVar);
    }

    CompareInst.Predicate compPredicate = null;
    BinaryOperator.Op binaryOp = null;
    String boolOp = null;

    if(operation.getOp().toString().equals(">="))
    {
      System.out.println("It is >=");
      compPredicate = CompareInst.Predicate.GE;
    }else if(operation.getOp().toString().equals("<="))
    {
      System.out.println("It is <=");
      compPredicate = CompareInst.Predicate.LE;
    }else if(operation.getOp().toString().equals(">"))
    {
      System.out.println("It is >");
      compPredicate = CompareInst.Predicate.GT;
    }else if(operation.getOp().toString().equals("<"))
    {
      System.out.println("It is <");
      compPredicate = CompareInst.Predicate.LT;
    }else if(operation.getOp().toString().equals("=="))
    {
      System.out.println("It is ==");
      compPredicate = CompareInst.Predicate.EQ;
    }else if(operation.getOp().toString().equals("!="))
    {
      System.out.println("It is !=");
      compPredicate = CompareInst.Predicate.NE;
    }else if(operation.getOp().toString().equals("&&"))
    {
      System.out.println("It is and");
      boolOp = "&&";
    }else if(operation.getOp().toString().equals("||"))
    {
      System.out.println("It is or");
      boolOp = "||";
    }else if(operation.getOp().toString().equals("+"))
    {
      System.out.println("it is add");
      binaryOp = BinaryOperator.Op.Add;
    }else if(operation.getOp().toString().equals("-"))
    {
      System.out.println("It is subtraction");
      binaryOp = BinaryOperator.Op.Sub;
    }else if(operation.getOp().toString().equals("*"))
    {
      System.out.println("It is multiply");
      binaryOp = BinaryOperator.Op.Mul;
    }else if(operation.getOp().toString().equals("/"))
    {
      System.out.println("It is divide");
      binaryOp = BinaryOperator.Op.Div;
    }

    if(boolOp != null)
    {
      System.out.println("It is boolOp");
      if(boolOp.equals("||"))
      {
        System.out.println("It is or");
        var destVar = mCurrentFunction.getTempVar(new BoolType());
        JumpInst jumpInst = new JumpInst(lhs.get_localVal());
        lhs.set_edge(jumpInst);

        NopInst mergeInst = new NopInst();

        InstPair rhs = operation.getRight().accept(this);
        jumpInst.setNext(0, rhs.get_start());
        CopyInst copyInst1 = new CopyInst(destVar, rhs.get_localVal());
        rhs.set_edge(copyInst1);

        CopyInst copyInst2 = new CopyInst(destVar, lhs.get_localVal());
        jumpInst.setNext(1, copyInst2);

        copyInst1.setNext(0, mergeInst);
        copyInst2.setNext(0, mergeInst);
        return new InstPair(lhs.get_start(), mergeInst, destVar);

      }else if(boolOp.equals("&&"))
      {
        System.out.println("It is and");
        var destVar = mCurrentFunction.getTempVar(new BoolType());
        JumpInst jumpInst = new JumpInst(lhs.get_localVal());
        lhs.set_edge(jumpInst);

        NopInst mergeInst = new NopInst();

        InstPair rhs = operation.getRight().accept(this);
        jumpInst.setNext(1, rhs.get_start());
        CopyInst copyInst1 = new CopyInst(destVar, rhs.get_localVal());
        rhs.set_edge(copyInst1);

        CopyInst copyInst2 = new CopyInst(destVar, lhs.get_localVal());
        jumpInst.setNext(0, copyInst2);

        copyInst1.setNext(0, mergeInst);
        copyInst2.setNext(0, mergeInst);
        return new InstPair(lhs.get_start(), mergeInst, destVar);
      }
    }

    //processing the rhs
    System.out.println("We visit rhs here");
    InstPair rhs = operation.getRight().accept(this);
    if(compPredicate != null)
    {
      System.out.println("it is compPredicate");
      var destVar = mCurrentFunction.getTempVar(new BoolType());
      CompareInst compareInst = new CompareInst(destVar, compPredicate, lhs.get_localVal(), rhs.get_localVal());
      lhs.set_edge(rhs.get_start());
      rhs.set_edge(compareInst);
      return new InstPair(lhs.get_start(), compareInst, destVar);
    }else if(binaryOp != null)
    {
      System.out.println("It is binaryOp");
      var destVar = mCurrentFunction.getTempVar(new IntType());
      BinaryOperator binaryOperator = new BinaryOperator(binaryOp, destVar, lhs.get_localVal(), rhs.get_localVal());
      System.out.println(lhs.get_localVal());
      System.out.println(rhs.get_localVal());
      lhs.set_edge(rhs.get_start());
      rhs.set_edge(binaryOperator);
      return new InstPair(lhs.get_start(), binaryOperator, destVar);
    }
    NopInst start = new NopInst();
    Instruction end = start;

    return new InstPair(start, end);
  }

  /*
  private InstPair visit(Expression expression) {


    return null;
  }
  */

  /**
   * It should compute the address into the array, do the load, and return the value in a LocalVar.
   */
  @Override
  public InstPair visit(ArrayAccess access) {
    InstPair instPair = access.getIndex().accept(this);
    var addressVar = mCurrentFunction.getTempAddressVar(access.getType());
    AddressAt addressAt = new AddressAt(addressVar, access.getBase(), instPair.get_localVal());
    var loadVar = mCurrentFunction.getTempVar(access.getType());
    LoadInst loadInst = new LoadInst(loadVar, addressVar);
    instPair.set_edge(addressAt);
    addressAt.setNext(0, loadInst);
    return new InstPair(instPair.get_start(), loadInst, loadVar);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralBool literalBool) {
    var destVar = mCurrentFunction.getTempVar(new BoolType());
    BooleanConstant booleanConstant = BooleanConstant.get(mCurrentProgram, literalBool.getValue());
    var copyInst = new CopyInst(destVar, booleanConstant);
    return new InstPair(copyInst, destVar);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralInt literalInt) {
    var destVar = mCurrentFunction.getTempVar(new IntType());
    IntegerConstant integerConstant = IntegerConstant.get(mCurrentProgram, literalInt.getValue());
    var copyInst = new CopyInst(destVar, integerConstant);

    return new InstPair(copyInst, destVar);
  }

  /**
   * Lower a Return.
   */
  @Override
  public InstPair visit(Return ret) {
    InstPair instPair = ret.getValue().accept(this);
    ReturnInst returnInst = new ReturnInst(instPair.get_localVal());
    instPair.set_edge(returnInst);

    return new InstPair(instPair.get_start(), returnInst);
  }

  /**
   * Break Node
   */
  @Override
  public InstPair visit(Break brk) {
    return new InstPair(instructionStack.peek(), new NopInst());
  }

  /**
   * Implement If Then Else statements.
   */
  @Override
  public InstPair visit(IfElseBranch ifElseBranch) {
    InstPair instPair = ifElseBranch.getCondition().accept(this);
    JumpInst jumpInst = new JumpInst(instPair.get_localVal());
    instPair.set_edge(jumpInst);

    NopInst mergeInst = new NopInst();

    InstPair instPairElse = ifElseBranch.getElseBlock().accept(this);
    jumpInst.setNext(0, instPairElse.get_start());
    instPairElse.set_edge(mergeInst);


    InstPair instPairThen  = ifElseBranch.getThenBlock().accept(this);
    jumpInst.setNext(1, instPairThen.get_start());
    instPairThen.set_edge(mergeInst);
    return new InstPair(instPair.get_start(), mergeInst);
  }

  /**
   * Implement for loops.
   */
  @Override
  public InstPair visit(For loop) {
    NopInst loopExit = new NopInst();
    instructionStack.push(loopExit);
    InstPair init = loop.getInit().accept(this);
    InstPair condition = loop.getCond().accept(this);

    init.set_edge(condition.get_start());

    JumpInst jumpInst = new JumpInst(condition.get_localVal());
    condition.set_edge(jumpInst);
    jumpInst.setNext(0, loopExit);
    InstPair body = loop.getBody().accept(this);

    jumpInst.setNext(1, body.get_start());
    InstPair increment = loop.getIncrement().accept(this);

    body.set_edge(increment.get_start());
    increment.set_edge(condition.get_start());
    instructionStack.pop();

    return new InstPair(init.get_start(), loopExit);
  }
}
