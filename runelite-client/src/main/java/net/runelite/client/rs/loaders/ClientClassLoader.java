package net.runelite.client.rs.loaders;

import javassist.ClassPool;
import javassist.LoaderClassPath;
import net.runelite.client.rs.hook.NetworkHook;
import net.runelite.client.rs.hook.RsaHook;

import java.net.URL;
import java.net.URLClassLoader;

public class ClientClassLoader extends LegacyClientClassLoader {

    private final URLClassLoader urlClassLoader;


    public ClientClassLoader(URL[] urls, String initialClass, NetworkHook networkHook, RsaHook rsaHook) {
        super(null, initialClass, networkHook, rsaHook);

        this.urlClassLoader = new URLClassLoader(urls);
    }

    @Override
    protected void loadClassPath(ClassPool cp, String name) {
        cp.appendClassPath(new LoaderClassPath(new URLClassLoader(urlClassLoader.getURLs())));
    }
}
