package net.runelite.client.rs.hook;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

public abstract class Hook {

    private final String id;

    public Hook(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public abstract void hook(ClassPool cp, CtClass cc) throws NotFoundException, CannotCompileException;
}
