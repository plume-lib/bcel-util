package org.plumelib.bcelutil;

import org.apache.bcel.generic.AALOAD;
import org.apache.bcel.generic.AASTORE;
import org.apache.bcel.generic.ACONST_NULL;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.ANEWARRAY;
import org.apache.bcel.generic.ARETURN;
import org.apache.bcel.generic.ARRAYLENGTH;
import org.apache.bcel.generic.ASTORE;
import org.apache.bcel.generic.ATHROW;
import org.apache.bcel.generic.BALOAD;
import org.apache.bcel.generic.BASTORE;
import org.apache.bcel.generic.BIPUSH;
import org.apache.bcel.generic.BREAKPOINT;
import org.apache.bcel.generic.CALOAD;
import org.apache.bcel.generic.CASTORE;
import org.apache.bcel.generic.CHECKCAST;
import org.apache.bcel.generic.CPInstruction;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.D2F;
import org.apache.bcel.generic.D2I;
import org.apache.bcel.generic.D2L;
import org.apache.bcel.generic.DADD;
import org.apache.bcel.generic.DALOAD;
import org.apache.bcel.generic.DASTORE;
import org.apache.bcel.generic.DCMPG;
import org.apache.bcel.generic.DCMPL;
import org.apache.bcel.generic.DCONST;
import org.apache.bcel.generic.DDIV;
import org.apache.bcel.generic.DLOAD;
import org.apache.bcel.generic.DMUL;
import org.apache.bcel.generic.DNEG;
import org.apache.bcel.generic.DREM;
import org.apache.bcel.generic.DRETURN;
import org.apache.bcel.generic.DSTORE;
import org.apache.bcel.generic.DSUB;
import org.apache.bcel.generic.DUP;
import org.apache.bcel.generic.DUP2;
import org.apache.bcel.generic.DUP2_X1;
import org.apache.bcel.generic.DUP2_X2;
import org.apache.bcel.generic.DUP_X1;
import org.apache.bcel.generic.DUP_X2;
import org.apache.bcel.generic.F2D;
import org.apache.bcel.generic.F2I;
import org.apache.bcel.generic.F2L;
import org.apache.bcel.generic.FADD;
import org.apache.bcel.generic.FALOAD;
import org.apache.bcel.generic.FASTORE;
import org.apache.bcel.generic.FCMPG;
import org.apache.bcel.generic.FCMPL;
import org.apache.bcel.generic.FCONST;
import org.apache.bcel.generic.FDIV;
import org.apache.bcel.generic.FLOAD;
import org.apache.bcel.generic.FMUL;
import org.apache.bcel.generic.FNEG;
import org.apache.bcel.generic.FREM;
import org.apache.bcel.generic.FRETURN;
import org.apache.bcel.generic.FSTORE;
import org.apache.bcel.generic.FSUB;
import org.apache.bcel.generic.FieldInstruction;
import org.apache.bcel.generic.GETFIELD;
import org.apache.bcel.generic.GETSTATIC;
import org.apache.bcel.generic.GOTO;
import org.apache.bcel.generic.GOTO_W;
import org.apache.bcel.generic.I2B;
import org.apache.bcel.generic.I2C;
import org.apache.bcel.generic.I2D;
import org.apache.bcel.generic.I2F;
import org.apache.bcel.generic.I2L;
import org.apache.bcel.generic.I2S;
import org.apache.bcel.generic.IADD;
import org.apache.bcel.generic.IALOAD;
import org.apache.bcel.generic.IAND;
import org.apache.bcel.generic.IASTORE;
import org.apache.bcel.generic.ICONST;
import org.apache.bcel.generic.IDIV;
import org.apache.bcel.generic.IFEQ;
import org.apache.bcel.generic.IFGE;
import org.apache.bcel.generic.IFGT;
import org.apache.bcel.generic.IFLE;
import org.apache.bcel.generic.IFLT;
import org.apache.bcel.generic.IFNE;
import org.apache.bcel.generic.IFNONNULL;
import org.apache.bcel.generic.IFNULL;
import org.apache.bcel.generic.IF_ACMPEQ;
import org.apache.bcel.generic.IF_ACMPNE;
import org.apache.bcel.generic.IF_ICMPEQ;
import org.apache.bcel.generic.IF_ICMPGE;
import org.apache.bcel.generic.IF_ICMPGT;
import org.apache.bcel.generic.IF_ICMPLE;
import org.apache.bcel.generic.IF_ICMPLT;
import org.apache.bcel.generic.IF_ICMPNE;
import org.apache.bcel.generic.IINC;
import org.apache.bcel.generic.ILOAD;
import org.apache.bcel.generic.IMPDEP1;
import org.apache.bcel.generic.IMPDEP2;
import org.apache.bcel.generic.IMUL;
import org.apache.bcel.generic.INEG;
import org.apache.bcel.generic.INSTANCEOF;
import org.apache.bcel.generic.INVOKEDYNAMIC;
import org.apache.bcel.generic.INVOKEINTERFACE;
import org.apache.bcel.generic.INVOKESPECIAL;
import org.apache.bcel.generic.INVOKESTATIC;
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.IOR;
import org.apache.bcel.generic.IREM;
import org.apache.bcel.generic.IRETURN;
import org.apache.bcel.generic.ISHL;
import org.apache.bcel.generic.ISHR;
import org.apache.bcel.generic.ISTORE;
import org.apache.bcel.generic.ISUB;
import org.apache.bcel.generic.IUSHR;
import org.apache.bcel.generic.IXOR;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.JSR;
import org.apache.bcel.generic.JSR_W;
import org.apache.bcel.generic.L2D;
import org.apache.bcel.generic.L2F;
import org.apache.bcel.generic.L2I;
import org.apache.bcel.generic.LADD;
import org.apache.bcel.generic.LALOAD;
import org.apache.bcel.generic.LAND;
import org.apache.bcel.generic.LASTORE;
import org.apache.bcel.generic.LCMP;
import org.apache.bcel.generic.LCONST;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.LDC2_W;
import org.apache.bcel.generic.LDC_W;
import org.apache.bcel.generic.LDIV;
import org.apache.bcel.generic.LLOAD;
import org.apache.bcel.generic.LMUL;
import org.apache.bcel.generic.LNEG;
import org.apache.bcel.generic.LOOKUPSWITCH;
import org.apache.bcel.generic.LOR;
import org.apache.bcel.generic.LREM;
import org.apache.bcel.generic.LRETURN;
import org.apache.bcel.generic.LSHL;
import org.apache.bcel.generic.LSHR;
import org.apache.bcel.generic.LSTORE;
import org.apache.bcel.generic.LSUB;
import org.apache.bcel.generic.LUSHR;
import org.apache.bcel.generic.LXOR;
import org.apache.bcel.generic.LoadClass;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.LocalVariableInstruction;
import org.apache.bcel.generic.MONITORENTER;
import org.apache.bcel.generic.MONITOREXIT;
import org.apache.bcel.generic.MULTIANEWARRAY;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.NEW;
import org.apache.bcel.generic.NEWARRAY;
import org.apache.bcel.generic.NOP;
import org.apache.bcel.generic.POP;
import org.apache.bcel.generic.POP2;
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.PUTSTATIC;
import org.apache.bcel.generic.RET;
import org.apache.bcel.generic.RETURN;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.SALOAD;
import org.apache.bcel.generic.SASTORE;
import org.apache.bcel.generic.SIPUSH;
import org.apache.bcel.generic.SWAP;
import org.apache.bcel.generic.StackConsumer;
import org.apache.bcel.generic.StackInstruction;
import org.apache.bcel.generic.StackProducer;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.generic.TABLESWITCH;
import org.apache.bcel.verifier.structurals.Frame;
import org.apache.bcel.verifier.structurals.InstConstraintVisitor;

/**
 * This class is dummy instruction constraint visitor that does no constraint checking at all. It is
 * used by StackVer as a replacement for org.apache.bcel.verifier.structurals.InstConstraintVisitor.
 * InstConstraintVisitor appears to be quite out of date and incorrectly fails on many valid class
 * files. Hence, StackVer assumes the method is valid and is only interested in the result of the
 * symbolic execution in order to capture the state of the local variables and stack at the start of
 * each byte code instruction.
 */
public class NoConstraintsVisitor extends InstConstraintVisitor {

  /** The constructor. Constructs a new instance of this class. */
  public NoConstraintsVisitor() {}

  @Override
  public void setFrame(Frame f) {}

  @Override
  public void setConstantPoolGen(ConstantPoolGen cpg) {}

  @Override
  public void setMethodGen(MethodGen mg) {}

  @Override
  public void visitLoadClass(LoadClass o) {}

  @Override
  public void visitStackConsumer(StackConsumer o) {}

  @Override
  public void visitStackProducer(StackProducer o) {}

  @Override
  public void visitCPInstruction(CPInstruction o) {}

  @Override
  public void visitFieldInstruction(FieldInstruction o) {}

  @Override
  public void visitInvokeInstruction(InvokeInstruction o) {}

  @Override
  public void visitStackInstruction(StackInstruction o) {}

  @Override
  public void visitLocalVariableInstruction(LocalVariableInstruction o) {}

  @Override
  public void visitLoadInstruction(LoadInstruction o) {}

  @Override
  public void visitStoreInstruction(StoreInstruction o) {}

  @Override
  public void visitReturnInstruction(ReturnInstruction o) {}

  @Override
  public void visitAALOAD(AALOAD o) {}

  @Override
  public void visitAASTORE(AASTORE o) {}

  @Override
  public void visitACONST_NULL(ACONST_NULL o) {}

  @Override
  public void visitALOAD(ALOAD o) {}

  @Override
  public void visitANEWARRAY(ANEWARRAY o) {}

  @Override
  public void visitARETURN(ARETURN o) {}

  @Override
  public void visitARRAYLENGTH(ARRAYLENGTH o) {}

  @Override
  public void visitASTORE(ASTORE o) {}

  @Override
  public void visitATHROW(ATHROW o) {}

  @Override
  public void visitBALOAD(BALOAD o) {}

  @Override
  public void visitBASTORE(BASTORE o) {}

  @Override
  public void visitBIPUSH(BIPUSH o) {}

  @Override
  public void visitBREAKPOINT(BREAKPOINT o) {}

  @Override
  public void visitCALOAD(CALOAD o) {}

  @Override
  public void visitCASTORE(CASTORE o) {}

  @Override
  public void visitCHECKCAST(CHECKCAST o) {}

  @Override
  public void visitD2F(D2F o) {}

  @Override
  public void visitD2I(D2I o) {}

  @Override
  public void visitD2L(D2L o) {}

  @Override
  public void visitDADD(DADD o) {}

  @Override
  public void visitDALOAD(DALOAD o) {}

  @Override
  public void visitDASTORE(DASTORE o) {}

  @Override
  public void visitDCMPG(DCMPG o) {}

  @Override
  public void visitDCMPL(DCMPL o) {}

  @Override
  public void visitDCONST(DCONST o) {}

  @Override
  public void visitDDIV(DDIV o) {}

  @Override
  public void visitDLOAD(DLOAD o) {}

  @Override
  public void visitDMUL(DMUL o) {}

  @Override
  public void visitDNEG(DNEG o) {}

  @Override
  public void visitDREM(DREM o) {}

  @Override
  public void visitDRETURN(DRETURN o) {}

  @Override
  public void visitDSTORE(DSTORE o) {}

  @Override
  public void visitDSUB(DSUB o) {}

  @Override
  public void visitDUP(DUP o) {}

  @Override
  public void visitDUP_X1(DUP_X1 o) {}

  @Override
  public void visitDUP_X2(DUP_X2 o) {}

  @Override
  public void visitDUP2(DUP2 o) {}

  @Override
  public void visitDUP2_X1(DUP2_X1 o) {}

  @Override
  public void visitDUP2_X2(DUP2_X2 o) {}

  @Override
  public void visitF2D(F2D o) {}

  @Override
  public void visitF2I(F2I o) {}

  @Override
  public void visitF2L(F2L o) {}

  @Override
  public void visitFADD(FADD o) {}

  @Override
  public void visitFALOAD(FALOAD o) {}

  @Override
  public void visitFASTORE(FASTORE o) {}

  @Override
  public void visitFCMPG(FCMPG o) {}

  @Override
  public void visitFCMPL(FCMPL o) {}

  @Override
  public void visitFCONST(FCONST o) {}

  @Override
  public void visitFDIV(FDIV o) {}

  @Override
  public void visitFLOAD(FLOAD o) {}

  @Override
  public void visitFMUL(FMUL o) {}

  @Override
  public void visitFNEG(FNEG o) {}

  @Override
  public void visitFREM(FREM o) {}

  @Override
  public void visitFRETURN(FRETURN o) {}

  @Override
  public void visitFSTORE(FSTORE o) {}

  @Override
  public void visitFSUB(FSUB o) {}

  @Override
  public void visitGETFIELD(GETFIELD o) {}

  @Override
  public void visitGETSTATIC(GETSTATIC o) {}

  @Override
  public void visitGOTO(GOTO o) {}

  @Override
  public void visitGOTO_W(GOTO_W o) {}

  @Override
  public void visitI2B(I2B o) {}

  @Override
  public void visitI2C(I2C o) {}

  @Override
  public void visitI2D(I2D o) {}

  @Override
  public void visitI2F(I2F o) {}

  @Override
  public void visitI2L(I2L o) {}

  @Override
  public void visitI2S(I2S o) {}

  @Override
  public void visitIADD(IADD o) {}

  @Override
  public void visitIALOAD(IALOAD o) {}

  @Override
  public void visitIAND(IAND o) {}

  @Override
  public void visitIASTORE(IASTORE o) {}

  @Override
  public void visitICONST(ICONST o) {}

  @Override
  public void visitIDIV(IDIV o) {}

  @Override
  public void visitIF_ACMPEQ(IF_ACMPEQ o) {}

  @Override
  public void visitIF_ACMPNE(IF_ACMPNE o) {}

  @Override
  public void visitIF_ICMPEQ(IF_ICMPEQ o) {}

  @Override
  public void visitIF_ICMPGE(IF_ICMPGE o) {}

  @Override
  public void visitIF_ICMPGT(IF_ICMPGT o) {}

  @Override
  public void visitIF_ICMPLE(IF_ICMPLE o) {}

  @Override
  public void visitIF_ICMPLT(IF_ICMPLT o) {}

  @Override
  public void visitIF_ICMPNE(IF_ICMPNE o) {}

  @Override
  public void visitIFEQ(IFEQ o) {}

  @Override
  public void visitIFGE(IFGE o) {}

  @Override
  public void visitIFGT(IFGT o) {}

  @Override
  public void visitIFLE(IFLE o) {}

  @Override
  public void visitIFLT(IFLT o) {}

  @Override
  public void visitIFNE(IFNE o) {}

  @Override
  public void visitIFNONNULL(IFNONNULL o) {}

  @Override
  public void visitIFNULL(IFNULL o) {}

  @Override
  public void visitIINC(IINC o) {}

  @Override
  public void visitILOAD(ILOAD o) {}

  @Override
  public void visitIMPDEP1(IMPDEP1 o) {}

  @Override
  public void visitIMPDEP2(IMPDEP2 o) {}

  @Override
  public void visitIMUL(IMUL o) {}

  @Override
  public void visitINEG(INEG o) {}

  @Override
  public void visitINSTANCEOF(INSTANCEOF o) {}

  @Override
  public void visitINVOKEDYNAMIC(INVOKEDYNAMIC o) {}

  @Override
  public void visitINVOKEINTERFACE(INVOKEINTERFACE o) {}

  @Override
  public void visitINVOKESPECIAL(INVOKESPECIAL o) {}

  @Override
  public void visitINVOKESTATIC(INVOKESTATIC o) {}

  @Override
  public void visitINVOKEVIRTUAL(INVOKEVIRTUAL o) {}

  @Override
  public void visitIOR(IOR o) {}

  @Override
  public void visitIREM(IREM o) {}

  @Override
  public void visitIRETURN(IRETURN o) {}

  @Override
  public void visitISHL(ISHL o) {}

  @Override
  public void visitISHR(ISHR o) {}

  @Override
  public void visitISTORE(ISTORE o) {}

  @Override
  public void visitISUB(ISUB o) {}

  @Override
  public void visitIUSHR(IUSHR o) {}

  @Override
  public void visitIXOR(IXOR o) {}

  @Override
  public void visitJSR(JSR o) {}

  @Override
  public void visitJSR_W(JSR_W o) {}

  @Override
  public void visitL2D(L2D o) {}

  @Override
  public void visitL2F(L2F o) {}

  @Override
  public void visitL2I(L2I o) {}

  @Override
  public void visitLADD(LADD o) {}

  @Override
  public void visitLALOAD(LALOAD o) {}

  @Override
  public void visitLAND(LAND o) {}

  @Override
  public void visitLASTORE(LASTORE o) {}

  @Override
  public void visitLCMP(LCMP o) {}

  @Override
  public void visitLCONST(LCONST o) {}

  @Override
  public void visitLDC(LDC o) {}

  @Override
  public void visitLDC_W(LDC_W o) {}

  @Override
  public void visitLDC2_W(LDC2_W o) {}

  @Override
  public void visitLDIV(LDIV o) {}

  @Override
  public void visitLLOAD(LLOAD o) {}

  @Override
  public void visitLMUL(LMUL o) {}

  @Override
  public void visitLNEG(LNEG o) {}

  @Override
  public void visitLOOKUPSWITCH(LOOKUPSWITCH o) {}

  @Override
  public void visitLOR(LOR o) {}

  @Override
  public void visitLREM(LREM o) {}

  @Override
  public void visitLRETURN(LRETURN o) {}

  @Override
  public void visitLSHL(LSHL o) {}

  @Override
  public void visitLSHR(LSHR o) {}

  @Override
  public void visitLSTORE(LSTORE o) {}

  @Override
  public void visitLSUB(LSUB o) {}

  @Override
  public void visitLUSHR(LUSHR o) {}

  @Override
  public void visitLXOR(LXOR o) {}

  @Override
  public void visitMONITORENTER(MONITORENTER o) {}

  @Override
  public void visitMONITOREXIT(MONITOREXIT o) {}

  @Override
  public void visitMULTIANEWARRAY(MULTIANEWARRAY o) {}

  @Override
  public void visitNEW(NEW o) {}

  @Override
  public void visitNEWARRAY(NEWARRAY o) {}

  @Override
  public void visitNOP(NOP o) {}

  @Override
  public void visitPOP(POP o) {}

  @Override
  public void visitPOP2(POP2 o) {}

  @Override
  public void visitPUTFIELD(PUTFIELD o) {}

  @Override
  public void visitPUTSTATIC(PUTSTATIC o) {}

  @Override
  public void visitRET(RET o) {}

  @Override
  public void visitRETURN(RETURN o) {}

  @Override
  public void visitSALOAD(SALOAD o) {}

  @Override
  public void visitSASTORE(SASTORE o) {}

  @Override
  public void visitSIPUSH(SIPUSH o) {}

  @Override
  public void visitSWAP(SWAP o) {}

  @Override
  public void visitTABLESWITCH(TABLESWITCH o) {}
}
