package net.runelite.client.rs.loaders;

import javafx.util.Pair;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import java.util.Map;

import static javassist.CtClass.byteType;
import static javassist.CtClass.intType;

public class Client181ClassLoader extends LegacyClientClassLoader {

	public Client181ClassLoader(Map<String, byte[]> classData, String initialClass) {
		super(classData, initialClass, new NetworkHook(), new RsaHook());
	}

	@SuppressWarnings({"unchecked"})
	static class NetworkHook extends net.runelite.client.rs.hook.NetworkHook {
		public NetworkHook() {
			super("fs");
		}

		@Override
		public void hook(ClassPool cp, CtClass cc) throws NotFoundException, CannotCompileException {
			CtClass object = cp.get(Object.class.getName());

			// Search for 'new Socket()' then search the parameter 'fw*.o ='
			hookGameHostAndPort(cc, new Pair[] {
				new Pair<>("w", new Pair<>(new CtClass[]{intType, intType, intType, object, byteType}, new int[] {2, 4})),
				new Pair<>("l", new Pair<>(new CtClass[]{intType, intType, intType, object}, new int[] {2, 4}))
			});
		}
	}

	static class RsaHook extends net.runelite.client.rs.hook.RsaHook {
		public RsaHook() {
			super("cy", "w");
		}
	}
}