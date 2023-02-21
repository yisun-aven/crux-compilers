package crux.ast.types;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.traversal.NullNodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class will associate types with the AST nodes from Stage 2
 */
public final class TypeChecker {
  private final ArrayList<String> errors = new ArrayList<>();

  public ArrayList<String> getErrors() {
    return errors;
  }

  public void check(DeclarationList ast) {
    var inferenceVisitor = new TypeInferenceVisitor();
    inferenceVisitor.visit(ast);
  }

  /**
   * Helper function, should be used to add error into the errors array
   */
  private void addTypeError(Node n, String message) {
    errors.add(String.format("TypeError%s[%s]", n.getPosition(), message));
  }

  /**
   * Helper function, should be used to record Types if the Type is an ErrorType then it will call
   * addTypeError
   */
  private void setNodeType(Node n, Type ty) {
    ((BaseNode) n).setType(ty);
    if (ty.getClass() == ErrorType.class) {
      var error = (ErrorType) ty;
      addTypeError(n, error.getMessage());
    }
  }

  /**
   * Helper to retrieve Type from the map
   */
  public Type getType(Node n) {
    return ((BaseNode) n).getType();
  }


  /**
   * This calls will visit each AST node and try to resolve it's type with the help of the
   * symbolTable.
   */
  private final class TypeInferenceVisitor extends NullNodeVisitor<Void> {

    private Symbol currentFunctionSymbol;
    private boolean lastStatementReturns;


    @Override
    public Void visit(VarAccess vaccess) {
      setNodeType(vaccess, vaccess.getSymbol().getType());
      return null;
    }

    @Override
    public Void visit(ArrayDeclaration arrayDeclaration) {
      ArrayType arrayType = (ArrayType) arrayDeclaration.getSymbol().getType();
      if(!(arrayType.getBase().getClass() == IntType.class) && !(arrayType.getBase().getClass() == BoolType.class))
      {
        setNodeType(arrayDeclaration, new ErrorType("Invalid type"));
      }
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(Assignment assignment) {
      var left = assignment.getLocation();
      var right = assignment.getValue();
      left.accept(this);
      right.accept(this);
      setNodeType(assignment, getType(left).assign(getType(right)));
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(Break brk) {
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(Call call) {
      TypeList callArgumentTypeList = new TypeList();
      for (Expression argument : call.getArguments()){
        // checking arguments type
        argument.accept(this);
        callArgumentTypeList.append(getType(argument));
      }
      setNodeType(call, call.getCallee().getType().call(callArgumentTypeList));
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(DeclarationList declarationList) {
      for (Node child :declarationList.getChildren()){
        child.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(FunctionDefinition functionDefinition) {
      String funcName = functionDefinition.getSymbol().getName();
      Symbol funcDef = functionDefinition.getSymbol();
      FuncType funcType = (FuncType) funcDef.getType();
      Type returnType = funcType.getRet();
      TypeList argumentTypeList = funcType.getArgs();

      lastStatementReturns = false;
      //visit function body
      functionDefinition.getStatements().accept(this);

      //update currentFunctionSymbol
      currentFunctionSymbol = funcDef;

      //Checking main
      if(funcName.equals("main"))
      {
        if(!returnType.toString().equals("void"))
        {
          setNodeType(functionDefinition, new ErrorType("main Function's return type must be void"));
        }
        if(!argumentTypeList.isEmpty())
        {
          setNodeType(functionDefinition, new ErrorType("main Function's argument has to be empty"));
        }
      }

      //check if there exists return type when return type is not void.
      if(!returnType.toString().equals("void"))
      {
        if(!lastStatementReturns)
        {
          setNodeType(functionDefinition, new ErrorType("there is no return type when the return type is not void"));
        }
      }

      return null;
    }

    @Override
    public Void visit(IfElseBranch ifElseBranch) {
      ifElseBranch.getCondition().accept(this);
      var condition = ifElseBranch.getCondition();

      //Check if the condition is BoolType
      if(!(getType(condition).getClass().equals(BoolType.class)))
      {
        setNodeType(ifElseBranch, new ErrorType("Condition is not BoolType"));
      }

      //Visit the else and then block
      var elseBlock = ifElseBranch.getElseBlock();
      var thenBlock = ifElseBranch.getThenBlock();
      lastStatementReturns = false;
      elseBlock.accept(this);
      boolean tempLastStatementReturns = lastStatementReturns;
      lastStatementReturns = false;
      thenBlock.accept(this);
      if(lastStatementReturns && tempLastStatementReturns)
      {
        lastStatementReturns = true;
      }else{
        lastStatementReturns = false;
      }

      return null;
    }


    @Override
    public Void visit(ArrayAccess access) {
      if (access.getIndex() == null){
        setNodeType(access, access.getBase().getType());
      } else {
        Node index = access.getIndex();
        index.accept(this);
        setNodeType(access, access.getBase().getType().index(getType(index)));
      }
      return null;
    }

    @Override
    public Void visit(LiteralBool literalBool) {
      setNodeType(literalBool, new BoolType());
      return null;
    }

    @Override
    public Void visit(LiteralInt literalInt) {
      setNodeType(literalInt, new IntType());
      return null;
    }

    @Override
    public Void visit(For forloop) {
      var condition = forloop.getCond();

      // Visit condition
      condition.accept(this);

      //Check if the condition is BoolType
      if(!(getType(condition).getClass().equals(BoolType.class)))
      {
        setNodeType(forloop, new ErrorType("Condition is not BoolType"));
      }

      //Visit Children
      var init = forloop.getInit();
      init.accept(this);
      var increment = forloop.getIncrement();
      increment.accept(this);
      var body = forloop.getBody();
      body.accept(this);

      return null;
    }

    @Override
    public Void visit(OpExpr op) {
      var left = op.getLeft();
      var right = op.getRight();

      // Visit left and right children
      left.accept(this);
      if(right != null)
      {
        right.accept(this);
      }

      //Call corresponding method depending on operator
      if(op.getOp().toString().equals("+"))
      {
        setNodeType(op, getType(left).add(getType(right)));
      }else if(op.getOp().toString().equals("-"))
      {
        setNodeType(op, getType(left).sub(getType(right)));
      }else if(op.getOp().toString().equals("*"))
      {
        setNodeType(op, getType(left).mul(getType(right)));
      }else if(op.getOp().toString().equals("/"))
      {
        setNodeType(op, getType(left).div(getType(right)) );
      }else if(op.getOp().toString().equals(">="))
      {
        setNodeType(op, getType(left).compare(getType(right)) );
      }else if(op.getOp().toString().equals("<="))
      {
        setNodeType(op, getType(left).compare(getType(right)) );
      }else if(op.getOp().toString().equals("=="))
      {
        setNodeType(op, getType(left).compare(getType(right)) );
      }else if(op.getOp().toString().equals("!="))
      {
        setNodeType(op, getType(left).compare(getType(right)) );
      }else if(op.getOp().toString().equals("<"))
      {
        setNodeType(op, getType(left).compare(getType(right)) );
      }else if(op.getOp().toString().equals(">"))
      {
        setNodeType(op, getType(left).compare(getType(right)) );
      }else if(op.getOp().toString().equals("=="))
      {
        setNodeType(op, getType(left).compare(getType(right)) );
      }else if(op.getOp().toString().equals("&&"))
      {
        setNodeType(op, getType(left).and(getType(right)) );
      }else if(op.getOp().toString().equals("||"))
      {
        setNodeType(op, getType(left).or(getType(right)) );
      }else if(op.getOp().toString().equals("!"))
      {
        setNodeType(op, getType(left).not());
      }else
      {
        setNodeType(op, new ErrorType("no operation error"));
      }

      return null;
    }

    @Override
    public Void visit(Return ret) {
      ret.getValue().accept(this);
      lastStatementReturns = true;
      return null;
    }


    @Override
    public Void visit(StatementList statementList) {
      for (Node child : statementList.getChildren()){
        if(lastStatementReturns)
        {
          setNodeType(statementList, new ErrorType("Unreachable statement"));

        }else
          child.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDeclaration) {
      if(!(variableDeclaration.getSymbol().getType().getClass() == IntType.class) && !(variableDeclaration.getSymbol().getType().getClass() == BoolType.class))
      {
        setNodeType(variableDeclaration, new ErrorType("Invalid type"));
      }
      lastStatementReturns = false;
      return null;
    }

  }
}
