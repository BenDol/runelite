package net.runelite.client.rs.hook;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.Modifier;
import javassist.NotFoundException;

import java.math.BigInteger;

import static net.runelite.client.RuneLite.GAME_RSA_MOD;

@SuppressWarnings("ConstantConditions")
public abstract class RsaHook extends Hook {

    private final String modVarName;

    public RsaHook(String id, String modVarName) {
        super(id);

        this.modVarName = modVarName;
    }

    @Override
    public void hook(ClassPool cp, CtClass cc) throws CannotCompileException, NotFoundException {
        if (GAME_RSA_MOD != null) {
            CtField mod = cc.getDeclaredField(modVarName);
            cc.removeField(mod);
            mod.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
            cc.addField(mod, CtField.Initializer.byExpr("new "+ BigInteger.class.getName() + "(\"" + GAME_RSA_MOD + "\", 16)"));
        }
    }

    public void hookInitialClass(ClassPool cp, CtClass cc) throws CannotCompileException {
        if (GAME_RSA_MOD != null) {
            CtConstructor ctor = cc.getConstructors()[0];
            ctor.insertBefore("System.out.println(\"[Hook] RSA '\"+" + getId() + "."+modVarName+"+\"'\");");
            ctor.insertBefore(getId() + "."+modVarName+" = new " + BigInteger.class.getName() + "(\"" + GAME_RSA_MOD + "\", 16);");
        }
    }

    public final String getModVarName() {
        return modVarName;
    }
}
