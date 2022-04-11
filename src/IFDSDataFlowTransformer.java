import java.util.Map;
import java.util.Set;

import heros.IFDSTabulationProblem;
import heros.InterproceduralCFG;
import heros.solver.IFDSSolver;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.toolkits.ide.exampleproblems.IFDSReachingDefinitions;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.scalar.Pair;

import soot.*;
import soot.jimple.*;

// 这个类和整个文件都没什么用了。
public class IFDSDataFlowTransformer extends SceneTransformer {

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        JimpleBasedInterproceduralCFG icfg= new JimpleBasedInterproceduralCFG();
        IFDSTabulationProblem<Unit, Pair<Value,
                Set<DefinitionStmt>>, SootMethod, 
                InterproceduralCFG<Unit, SootMethod>> problem = new IFDSReachingDefinitions(icfg);

        IFDSSolver<Unit, Pair<Value, Set<DefinitionStmt>>, 
                SootMethod, InterproceduralCFG<Unit, SootMethod>> solver = 
                    new IFDSSolver<Unit, Pair<Value, Set<DefinitionStmt>>, SootMethod, 
                                   InterproceduralCFG<Unit, SootMethod>>(problem);

        System.out.println("Starting IFDSReachingDefinitions.");
        solver.solve();
        System.out.println("Done");

        SootMethod abi5go = Scene.v().getSootClass("org.cryptoapi.bench.brokencrypto.BrokenCryptoABICase5").getMethodByName("go");
        Body abi5go2body2 = abi5go.retrieveActiveBody();
        // SootClass mainClass = Scene.v().getMainClass(); // get main class
        SootClass abicase5 = Scene.v().getSootClass("org.cryptoapi.bench.brokencrypto.BrokenCryptoABICase5");
        // iter each method, iter each statement, finally String.valueOf
        SootMethod go = abicase5.getMethodByName("go");
        for (Unit u : go.retrieveActiveBody().getUnits()) {
            Stmt stmt = (Stmt) u;
//            System.out.printf("%s\n", stmt);
            if (stmt.containsInvokeExpr()) {
                InvokeExpr invokeExpr = stmt.getInvokeExpr();
                if (invokeExpr.getMethod().getName().equals("valueOf")) {
                    Value arg0 = invokeExpr.getArg(0);
                    Set<Pair<Value, Set<DefinitionStmt>>> defs = solver.ifdsResultsAt(stmt);
                    DefinitionStmt defst = findInSet(defs, arg0);
                    Value staticfield = defst.getRightOp();
                    defs = solver.ifdsResultsAt(defst);
                    System.out.println(defs.toString());
                }
//                String class_name = invokeExpr.getMethod().getDeclaringClass().getName(); // java.lang.String
//                System.out.printf("%s\n", name);
            }
        }

        System.out.println("OK.");
    }

    private DefinitionStmt findInSet(Set<Pair<Value, Set<DefinitionStmt>>> defs, Value arg0) {
        Set<DefinitionStmt> target = null;
        for (Pair<Value, Set<DefinitionStmt>> pair : defs){
            if (pair.getO1().equals(arg0)){
                target = pair.getO2();
            }
        }
        if (target == null) {
            return null;
        }
        if (target.size() != 1) {
            System.out.println("[!!!!] multi DefinitionStmt!!!.");
        }
        return target.iterator().next();
    }
}