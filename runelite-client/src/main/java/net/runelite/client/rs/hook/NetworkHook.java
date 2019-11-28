package net.runelite.client.rs.hook;

import javafx.util.Pair;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

import static net.runelite.client.RuneLite.GAME_HOST;
import static net.runelite.client.RuneLite.GAME_SSL_DISABLED;

@SuppressWarnings("ConstantConditions")
public abstract class NetworkHook extends Hook {

    public NetworkHook(String id) {
        super(id);
    }

    protected void hookGameHostAndPort(CtClass cc, Pair<String, Pair<CtClass[], int[]>>[] methodDefs) throws CannotCompileException, NotFoundException {
        for (Pair<String, Pair<CtClass[], int[]>> methodDef : methodDefs) {
            Pair<CtClass[], int[]> definition = methodDef.getValue();
            CtMethod method = cc.getDeclaredMethod(methodDef.getKey(), definition.getKey());

            int port = definition.getValue()[0];
            int host = definition.getValue()[1];

            setGameHostAndPortVars(method, host, port);
        }
    }

    protected void setGameHostAndPortVars(CtMethod method, int host, int port) throws CannotCompileException {
        if (GAME_HOST != null || GAME_SSL_DISABLED) {
            method.insertBefore("System.out.println(\"[Hook] Assigning '"+ method.getName() +"' Port: \" + $"+port+" + \", Host: \" + $"+host+");");
        }

        if (GAME_HOST != null) {
            method.insertBefore("if($" + host + " instanceof String) { $" + host + " = \"" + GAME_HOST + "\"; }");
        }

        if (GAME_SSL_DISABLED) {
            method.insertBefore("if ($"+port+" == 443) { $"+port+" = 80; }");
        }
    }
}
