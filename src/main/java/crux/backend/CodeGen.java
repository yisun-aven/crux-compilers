package crux.backend;

import crux.ast.SymbolTable.Symbol;
import crux.ir.*;
import crux.ir.insts.*;
import crux.printing.IRValueFormatter;

import java.util.*;

/**
 * Convert the CFG into Assembly Instructions
 */
public final class CodeGen extends InstVisitor {
  private final Program p;
  private final CodePrinter out;
  HashMap<Instruction, String> labelMap = new HashMap<>();
  private HashMap<Variable, Integer> varIndexMap = new HashMap<>();
  private int numLocalVar = 1;
  int numSlots;

  private Integer getLocalVarStackIndex(Variable varName){  // add to varStackMap if doesn't exists
    if(varIndexMap.containsKey(varName)){
      return varIndexMap.get(varName);
    } else {
      varIndexMap.put(varName, numLocalVar*(-8));
      return (-8)*(numLocalVar++);
    }
  }

  private final IRValueFormatter irFormat = new IRValueFormatter();
  private void printInstructionInfo(Instruction i){
      var info = String.format("/* %s */", i.format(irFormat));
      out.printCode(info);
  }


  public CodeGen(Program p) {
    this.p = p;
    // Do not change the file name that is outputted or it will
    // break the grader!

    out = new CodePrinter("a.s");
  }

  /**
   * It should allocate space for globals call genCode for each Function
   */
  public void genCode() {
    for(Iterator<GlobalDecl> glob_it = p.getGlobals(); glob_it.hasNext();)
    {

      GlobalDecl g = glob_it.next();
      //out.printCode(".comm _" + g.getSymbol().getName() + ", " + g.getNumElement().getValue() * 8 + ", 8");
      out.printCode(".comm " + g.getSymbol().getName() + ", " + g.getNumElement().getValue() * 8 + ", 8");
    }
    int count[] = new int[1];
    for(Iterator<Function> func_it = p.getFunctions(); func_it.hasNext();)
    {
      Function f = func_it.next();
      genCode(f, count);
    }
    out.close();
  }

  private void genCode(Function f, int[] count)
  {
    labelMap = f.assignLabels(count);

    out.printCode(".globl " + f.getName());
    out.printLabel( f.getName() + ":");


    numSlots = f.getNumTempVars() + f.getNumTempAddressVars();
    if(numSlots % 2 != 0)
    {
      numSlots++;
    }

    out.printCode("enter $(8 * " + numSlots + "), $0");

    List<LocalVar> args = f.getArguments();
    int num_arg = 1;
    int buf = 0;
    //int reg = 10;
    for(LocalVar arg: args) //"movq %rdi, "+ stackPos + "(%rbp)"
    {
      if(num_arg == 1)
      {
        varIndexMap.put(arg, -8);
        out.printCode("movq %rdi, " + "-8(%rbp)");
      }
      else if(num_arg == 2)
      {
        varIndexMap.put(arg, -16);
        out.printCode("movq %rsi, " + "-16(%rbp)");
      }
      else if(num_arg == 3)
      {
        varIndexMap.put(arg, -24);
        out.printCode("movq %rdx, " + "-24(rbp)");
      }
      else if(num_arg == 4)
      {
        varIndexMap.put(arg, -32);
        out.printCode("movq %rcx, " + "-32(rbp)");
      }
      else if(num_arg == 5)
      {
        varIndexMap.put(arg, -40);
        out.printCode("movq %r8, " + "-40(rbp)");
      }
      else if(num_arg == 6)
      {
        varIndexMap.put(arg, -48);
        out.printCode("movq %r9, " + "-48(rbp)");
      }
      else
      {

        varIndexMap.put(arg, num_arg * (-8));
        int overFlow = (buf * 8 + 16);
        out.printCode("movq " + overFlow + "(%rbp), %r10");
        out.printCode("movq %r10, " + num_arg *(-8) + "(%rbp)");


        buf++;



      }

      num_arg++;
      numLocalVar++;
    }


    Stack<Instruction> tovisited = new Stack<>();
    HashSet<Instruction> discovered = new HashSet<>();
    tovisited.push(f.getStart());
    while (!tovisited.isEmpty()) {
      Instruction inst = tovisited.pop();
      if (labelMap.containsKey(inst)){
        out.printLabel(labelMap.get(inst) + ":");
      }

      inst.accept(this);
      Instruction first = inst.getNext(0);
      Instruction second = inst.getNext(1);

      if ((second != null) && (!discovered.contains(second))){
        tovisited.push(second);
        discovered.add(second);
      }

      if(first != null)
      {
        if(!discovered.contains(first))
        {
          tovisited.push(first);
          discovered.add(first);
        }
        if((tovisited.isEmpty() || first != tovisited.peek()))
        {
          out.printCode("jmp " + labelMap.get(first));
        }
      }
      else
      {
        out.printCode("leave");
        out.printCode("ret");
      }


    }
  }


  public void visit(AddressAt i) {
    printInstructionInfo(i);
    AddressVar destVar = i.getDst();
    Symbol src = i.getBase();
    LocalVar offset = i.getOffset();
    int destPos = getLocalVarStackIndex(destVar);
    if(offset == null)
    {
        out.printCode("movq " + src.getName() + "@GOTPCREL(%rip), %r11");
        out.printCode("movq %r11, " + destPos + "(%rbp)");
    }else
    {
        out.printCode("movq " + getLocalVarStackIndex(offset) + "(%rbp), %r11");
        out.printCode("imulq $8, %r11");
        out.printCode("movq " + src.getName() + "@GOTPCREL(%rip), %r10");
        out.printCode("addq %r10, %r11");
        out.printCode("movq %r11, " + destPos + "(%rbp)");
    }
  }

  public void visit(BinaryOperator i) {
    printInstructionInfo(i);
    String opStr;

    switch (i.getOperator()){
      case Add: opStr = "addq"; break;

      case Sub: opStr = "subq"; break;

      case Mul: opStr = "imulq"; break;

      case Div: opStr = "idivq"; break;

      default: opStr = "ERROR"; break;
    }

    LocalVar leftOperand = i.getLeftOperand();
    LocalVar rightOperand = i.getRightOperand();
    LocalVar destOperand = i.getDst();
    int leftstack = getLocalVarStackIndex(leftOperand);
    int rightstack = getLocalVarStackIndex(rightOperand);
    int deststack = getLocalVarStackIndex(destOperand);

    if(opStr.equals("addq"))
    {
      out.printCode("movq " + leftstack + "(%rbp), %r10");
      out.printCode("addq " + rightstack + "(%rbp), %r10");
      out.printCode("movq %r10, " + deststack + "(%rbp)");
    }
    else if(opStr.equals("subq"))
    {
      //new fix
      out.printCode("movq " + leftstack + "(%rbp), %r10");
      out.printCode("subq " + rightstack + "(%rbp), %r10");
      out.printCode("movq %r10, " + deststack + "(%rbp)");
    }
    else if(opStr.equals("imulq"))
    {
      out.printCode("movq " + leftstack + "(%rbp), %r10");
      out.printCode("imulq " + rightstack + "(%rbp), %r10");
      out.printCode("movq %r10, " + deststack + "(%rbp)");
    }
    else if(opStr.equals("idivq"))
    {
      out.printCode("movq " + leftstack + "(%rbp), %rax");
      out.printCode("cqto");
      out.printCode("idivq " + rightstack + "(%rbp)");
      out.printCode("movq %rax, " + deststack + "(%rbp)");
    }

  }

  public void visit(CompareInst i) {
    printInstructionInfo(i);
    int dst = getLocalVarStackIndex(i.getDst());
    int leftHandSide = getLocalVarStackIndex(i.getLeftOperand());
    int rightHandSide = getLocalVarStackIndex(i.getRightOperand());
    out.printCode("movq $0, %rax");
    out.printCode("movq $1, %r10");
    out.printCode("movq " + leftHandSide + "(%rbp), %r11");
    out.printCode("cmp " + rightHandSide + "(%rbp), %r11");

    if(i.getPredicate() == CompareInst.Predicate.LE)
    {
      out.printCode("cmovle %r10, %rax");
    }else if(i.getPredicate() == CompareInst.Predicate.LT)
    {
      out.printCode("cmovl %r10, %rax");
    }else if(i.getPredicate() == CompareInst.Predicate.GT)
    {
      out.printCode("cmovg %r10, %rax");
    }else if(i.getPredicate() == CompareInst.Predicate.GE)
    {
      out.printCode("cmovge %r10, %rax");
    }else if(i.getPredicate() == CompareInst.Predicate.NE)
    {
      out.printCode("cmovne %r10, %rax");
    }else if(i.getPredicate() == CompareInst.Predicate.EQ)
    {
      out.printCode("cmove %r10, %rax");
    }
    out.printCode("movq %rax, " + dst + "(%rbp)");
  }

  public void visit(CopyInst i) {
    printInstructionInfo(i);
    var src = i.getSrcValue();
    int dst = getLocalVarStackIndex(i.getDstVar());
    if(src instanceof IntegerConstant)
    {
      out.printCode("movq " + "$" + ((IntegerConstant)src).getValue() + ", " + "%r10" ) ;
      out.printCode("movq %r10, " + dst + "(%rbp)");
    }else if(src instanceof BooleanConstant)
    {
      if(((BooleanConstant)src).getValue() == true)
      {
        out.printCode("movq " + "$1" + ", " + dst + "(%rbp)" );
      }
      else
      {
        out.printCode("movq " + "$0" + ", " + dst + "(%rbp)" );
      }
    }else if(src instanceof LocalVar)
    {
      out.printCode("movq " + getLocalVarStackIndex((LocalVar)src) + "(%rbp), %r10") ;
      out.printCode("movq " + "%r10" + ", " + dst + "(%rbp)");
    }

  }

  public void visit(JumpInst i) {
    printInstructionInfo(i);
    String jumpDst = labelMap.get(i.getNext(1));
    out.printCode("movq " + getLocalVarStackIndex(i.getPredicate()) + "(%rbp), %r10");
    out.printCode("cmp $1, %r10");
    out.printCode("je " + jumpDst);
  }

  public void visit(LoadInst i) {
    printInstructionInfo(i);
    AddressVar srcAddress = i.getSrcAddress();
    var dst = i.getDst();
    out.printCode("movq " + getLocalVarStackIndex(srcAddress) + "(%rbp), %r10");
    out.printCode("movq 0(%r10), %r11");
    out.printCode("movq %r11, " + getLocalVarStackIndex(dst) + "(%rbp)");
  }

  public void visit(NopInst i) {
    printInstructionInfo(i);
  }

  public void visit(StoreInst i) {
    printInstructionInfo(i);
    var srcVal = i.getSrcValue();
    var dstAddr = i.getDestAddress();
    out.printCode("movq " + getLocalVarStackIndex(dstAddr) + "(%rbp), %r10");
    out.printCode("movq " + getLocalVarStackIndex(srcVal) + "(%rbp), %r11");
    out.printCode("movq %r11, 0(%r10)");
  }


  public void visit(ReturnInst i) {
    printInstructionInfo(i);
    if(i.getReturnValue() != null)
    {
      out.printCode("movq " + getLocalVarStackIndex(i.getReturnValue()) + "(%rbp), %rax");
    }
    out.printCode("leave");
    out.printCode("ret");
  }



  public void visit(CallInst i) {
    out.printCode("/* callInst */");
    printInstructionInfo(i);
    String funcName = i.getCallee().getName();
    List<LocalVar> paramList = i.getParams();
    int paramIndex = 0;
    for (Variable param : paramList) {
      int stackPos = getLocalVarStackIndex(param);
      switch (paramIndex) {
        case (0):
          out.printCode("movq " + stackPos + "(%rbp)" + ", %rdi");
          break;
        case (1):
          out.printCode("movq " + stackPos + "(%rbp)" + ", %rsi");
          break;
        case (2):
          out.printCode("movq " + stackPos + "(%rbp)" + ", %rdx");
          break;
        case (3):
          out.printCode("movq " + stackPos + "(%rbp)" + ", %rcx");
          break;
        case (4):
          out.printCode("movq " + stackPos + "(%rbp)" + ", %r8");
          break;
        case (5):
          out.printCode("movq " + stackPos + "(%rbp)" + ", %r9");
          break;
        default:
          break;
      }
      paramIndex++;
    }


    if (i.getParams().size() > 6){
      for (int index = i.getParams().size() - 1; index > 5; index--){
        var par = i.getParams().get(index);
        int stackPos = getLocalVarStackIndex(par);
        out.printCode("movq " + stackPos + "(%rbp), %r10");
        out.printCode("movq %r10, " + (numLocalVar)*(-8) + "(%rbp)"); 
      }
    }

    // now after loading the params, we call the function
    out.printCode("call "+ funcName);
    var dstVar = i.getDst();
    if(dstVar != null)
    {
      out.printCode("movq %rax, " + getLocalVarStackIndex(dstVar) + "(%rbp)");
    }

  }

  public void visit(UnaryNotInst i) {
    printInstructionInfo(i);
    int dstStack = getLocalVarStackIndex(i.getDst());
    int innerStack = getLocalVarStackIndex(i.getInner());

    out.printCode("movq " + innerStack + "(%rbp), %r10");
    out.printCode("not %r10");
    out.printCode("movq %r10, " + dstStack + "(%rbp)");

  }
}
