package net.runelite.client.rs.loaders;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import net.runelite.client.rs.hook.NetworkHook;
import net.runelite.client.rs.hook.RsaHook;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

public class ClientClassLoader extends URLClassLoader {

    private final String initialClass;
    private final NetworkHook networkHook;
    private final RsaHook rsaHook;

    public ClientClassLoader(URL[] urls, String initialClass, NetworkHook networkHook, RsaHook rsaHook) {
        super(urls);

        this.initialClass = initialClass;
        this.networkHook = networkHook;
        this.rsaHook = rsaHook;
    }

    @Override

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        ClassPool cp = ClassPool.getDefault();

        try {
            cp.appendClassPath(new LoaderClassPath(new URLClassLoader(getURLs())));
            CtClass cc = cp.get(name);

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
