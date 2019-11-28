package net.runelite.client.rs.loaders;

import javassist.ByteArrayClassPath;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import net.runelite.client.rs.hook.NetworkHook;
import net.runelite.client.rs.hook.RsaHook;

import java.io.IOException;
import java.util.Map;

public class LegacyClientClassLoader extends ClassLoader {

    private final Map<String, byte[]> classData;
    private final String initialClass;
    private final NetworkHook networkHook;
    private final RsaHook rsaHook;

    public LegacyClientClassLoader(Map<String, byte[]> classData, String initialClass, NetworkHook networkHook, RsaHook rsaHook) {
        this.classData = classData;
        this.initialClass = initialClass;
        this.networkHook = networkHook;
        this.rsaHook = rsaHook;
    }

    protected void loadClassPath(ClassPool cp, String name) {
        byte[] data = classData.get(name);
        cp.appendClassPath(new ByteArrayClassPath(name, data));
    }

    public CtClass findClass(ClassPool cp, String name) throws NotFoundException {
        return cp.get(name);
    }

    protected CtClass hookClass(CtClass cc) throws NotFoundException, CannotCompileException {
        ClassPool cp = ClassPool.getDefault();
        String name = cc.getName();

        if (name.equals(networkHook.getId())) {
            networkHook.hook(cp, cc);
        }
        else if (name.equals(rsaHook.getId())) {
            rsaHook.hook(cp, cc);
        }
        else if(name.equals(initialClass)) {
            rsaHook.hookInitialClass(cp, cc);
        }
        else {
            cc = null;
        }
        return cc;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        ClassPool cp = ClassPool.getDefault();

        try {
            loadClassPath(cp, name);
            CtClass cc = hookClass(findClass(cp, name));

            if (cc != null) {
                byte[] data = cc.toBytecode();
                if (data == null) {
                    throw new ClassNotFoundException(name);
                }

                Class<?> clazz = defineClass(name, data, 0, data.length);
                cc.defrost();
                return clazz;
            }
        }
        catch (NotFoundException | CannotCompileException | IOException e) {
            e.printStackTrace();
        }

        return super.findClass(name);
    }

    public String getInitialClass() {
        return initialClass;
    }
}
