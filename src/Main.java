import javafx.util.Pair;
import soot.*;
import soot.jimple.*;
import soot.options.Options;
import soot.toolkits.scalar.LocalDefs;

import java.util.ArrayList;
import java.util.List;

public class Main {
    private static ClassFieldCrossReferenceAnalysis crossRef = null;

    public static void main(String[] args) {
        String classPath = "inputs/org-cryptoapi-bench-rigorityj-samples-1.0-SNAPSHOT.jar";
        configureSoot(classPath);// configure soot
        Scene.v().loadNecessaryClasses(); // load all the library and dependencies for given program
//        IFDSDataFlowTransformer mVNTransformer = new IFDSDataFlowTransformer();
//        Transform mVNTransform = new Transform("wjtp.valNumbering", mVNTransformer);
//        PackManager.v().getPack("wjtp").add(mVNTransform);// add in Whole-program Jimple Transformer(wjtp)
//        setEntryPoints(); // 在runPacks前面运行
        PackManager.v().runPacks();  // process and build call graph
        post_analysis();
    }

    private static void resolveDataflow(SootMethod initMth, Stmt initStmt) {
        // 观察解析的过程可以发现，我们追溯的都是赋值语句，而且总是已知左边要追溯右边的变量哪里来的。
        // 所以，根据右边是什么，执行不同的解析逻辑，！解析一次为一个循环！
        // 1 如果右边是函数调用 XXXinvoke语句，就打印一下函数是什么，把里面的参数拿出来，继续追溯参数。
        // 有几种情况，String.ValueOf是静态调用，staticinvoke，所以直接拿参数
        // toCharArray，是调用成员函数，没有参数，所以要把调用的对象拿出来
        // 2 如果右边是函数的成员
        // 就调用我们的交叉应用分析，找到赋值点语句，这个时候函数也变了，不再是原来的函数。
        // 3 如果右边是普通的局部变量
        // 调用我们的到达定值分析，找到变量的赋值点。
        // 4 结束的情况 TODO，看看结束的时候是什么情况，可能是常量字符串。最后根据字符串判断是不是不安全加密函数，并且报警。

        SootMethod currentMth = initMth; // 注意切换到其他函数的时候要更新，因为如果再要做Reaching-Definition分析要用到。
        Stmt currentStmt = initStmt;
    }

    private static void post_analysis() {
        doCrossRef(); // 收集类成员的使用
        System.out.println("=========post_analysis==========");
        // SootClass mainClass = Scene.v().getMainClass(); // get main class
        SootClass abicase5 = Scene.v().getSootClass("org.cryptoapi.bench.brokencrypto.BrokenCryptoABICase5");
        // 假设还是原来的代码，遍历到了这个函数
        SootMethod go = abicase5.getMethodByName("go");
        LocalDefs ld = G.v().soot_toolkits_scalar_LocalDefsFactory().newLocalDefs(go.getActiveBody());

        System.out.println("开始打印被分析的函数体");
        for (Unit u : go.getActiveBody().getUnits()) {
            Stmt stmt = (Stmt) u;
            System.out.println(stmt.toString());
        }
        System.out.println("函数体打印结束");

        for (Unit u : go.getActiveBody().getUnits()) {
            Stmt stmt = (Stmt) u;
            if (stmt.containsInvokeExpr()) {
                InvokeExpr invokeExpr = stmt.getInvokeExpr();
                // 假设分析Cipher.getInstance函数或者KeyGenerator.getInstance函数，分析到了String.valueOf函数，遍历的东西我就不写了。
                if (invokeExpr.getMethod().getName().equals("valueOf")) {
                    System.out.println("开始追溯String.valueOf的参数。\n开始语句: "+stmt.toString());
                    // $r5 = staticinvoke <java.lang.String: java.lang.String valueOf(char[])>($r4)
                    // 一般valueOf的参数都是一个局部变量，所以首先调用到达定值分析分析一下，找到下一个语句。
                    Value arg0 = invokeExpr.getArg(0);
                    System.out.println("String.valueOf的参数为: "+arg0.toString());
                    for (Unit next : ld.getDefsOfAt((Local) arg0, u)) { // 只会执行一次循环
                        Stmt stmt2 = (Stmt) next; // $r4 = <org.cryptoapi.bench.brokencrypto.BrokenCryptoABICase5: char[] crypto>
                        System.out.println("上一步的local的定义语句为: "+stmt2.toString());
                        if (!stmt2.containsFieldRef()) {
                            System.out.println("错误: 语句没有索引到静态域成员: "+stmt2.toString());
                            continue;
                        }
                        FieldRef field = stmt2.getFieldRef();
                        // 调用另外一个解析函数，这个函数负责找到（另外一个函数中）对这个成员的赋值点，
                        // <org.cryptoapi.bench.brokencrypto.BrokenCryptoABICase5: char[] crypto> = $r0
                        // 但是一般还是用的局部变量，所以还要用一次到达定值分析
                        Pair<Stmt,SootMethod> stmt3_pair = crossRef.getSigleDefine(field);
                        Stmt stmt3 = stmt3_pair.getKey();
                        Value rhs3 = ((AssignStmt) stmt3).getRightOp();
                        SootMethod m3 = stmt3_pair.getValue();
                        System.out.println("追溯到了其他函数: "+m3.toString());
                        System.out.println("追溯成员的其他赋值: "+stmt3.toString());
                        LocalDefs ld3 = G.v().soot_toolkits_scalar_LocalDefsFactory().newLocalDefs(m3.getActiveBody());
                        for (Unit next3 : ld3.getDefsOfAt((Local) rhs3, stmt3)) { // 只会执行一次循环
                            Stmt stmt4 = (Stmt) next3; // $r0 = <org.cryptoapi.bench.brokencrypto.BrokenCryptoABICase5: char[] CRYPTO>
                            System.out.println("上一个成员赋值的来源语句: "+stmt4.toString());
                            System.out.println("TODO 后面的代码待完善。"); // 在这里下断点。
                        }
                    }
                }
            }
        }
        System.out.println("Done.");
    }


    protected static void doCrossRef() {
        List<SootClass> entryPoints = new ArrayList<SootClass>();
        for (SootClass clz : Scene.v().getApplicationClasses()) {
            // 这里控制分析哪些函数
//            if (clz.getName().startsWith("org.cryptoapi.bench.brokencrypto.BrokenCryptoABI")){
            if (clz.getName().startsWith("org.cryptoapi.bench.brokencrypto.BrokenCryptoABICase5")){
                entryPoints.add(clz);
            }
        }
        crossRef = new ClassFieldCrossReferenceAnalysis(entryPoints);
    }

    // 没有用了
    protected static void setEntryPoints() {
        List<SootMethod> entryPoints = new ArrayList<SootMethod>();
        for (SootClass clz : Scene.v().getApplicationClasses()) {

            if (clz.getName().startsWith("org.cryptoapi.bench.brokencrypto.BrokenCryptoABI") && clz.declaresMethodByName("main")){
                SootMethod main = clz.getMethodByName("main");
                entryPoints.add(main);
            }
        }
        System.out.println("分析入口:");
        System.out.println(entryPoints.toString());
        Scene.v().setEntryPoints(entryPoints);

        Options.v().set_main_class("org.cryptoapi.bench.brokencrypto.BrokenCryptoABICase5");
    }

    // 分析时排除一些库函数，加快速度。
    protected static List<String> getExcludes() {
        List<String> excludeList = new ArrayList<>();
        //excludeList.add("java.*");
        excludeList.add("sun.*");
        excludeList.add("android.*");
        excludeList.add("org.apache.*");
        excludeList.add("org.eclipse.*");
        excludeList.add("soot.*");
        excludeList.add("javax.*");
        return excludeList;
    }

    // 没有用了
    protected static List<String> getIncludes() {
        List<String> excludeList = new ArrayList<>();
        excludeList.add("org.cryptoapi.*");
        return excludeList;
    }

    public static void configureSoot(String classpath) {
        Options.v().set_whole_program(true);  // process whole program
        Options.v().set_allow_phantom_refs(true); // load phantom references
        Options.v().set_prepend_classpath(true); // prepend class path
        Options.v().set_src_prec(Options.src_prec_class); // process only .class files, change here to process other IR or class
        Options.v().set_output_format(Options.output_format_jimple); // output jimple format, change here to output other IR
        ArrayList<String> list = new ArrayList<>();
        list.add(classpath);
        Options.v().set_process_dir(list); // process all .class files in directory
//        Options.v().setPhaseOption("cg.spark", "on"); // use spark for call graph

//        Options.v().set_soot_classpath(classpath);
//        Options.v().set_no_writeout_body_releasing(true);
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_exclude(getExcludes());
//        Options.v().set_include(getIncludes());
//        Options.v().set_app(true);
//        // Call-graph options
//        Options.v().setPhaseOption("cg", "safe-newinstance:true");
//        Options.v().setPhaseOption("cg.cha","enabled:false");
//
//        // Enable SPARK call-graph construction
//        Options.v().setPhaseOption("cg.spark","enabled:true");
//        Options.v().setPhaseOption("cg.spark","verbose:true");
//        Options.v().setPhaseOption("cg.spark","on-fly-cg:true");
        // RD tagger
//        Options.v().setPhaseOption("jap.rdtagger","enabled:true");
    }

}
