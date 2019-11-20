package net.runelite.client.rs.loaders;

import javafx.util.Pair;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.Modifier;
import javassist.NotFoundException;

import java.math.BigInteger;
import java.net.URL;

import static javassist.CtClass.byteType;
import static javassist.CtClass.intType;
import static net.runelite.client.RuneLite.GAME_RSA_MOD;

public class Client181ClassLoader extends ClientClassLoader {

	public Client181ClassLoader(URL[] urls, String initialClass) {
		super(urls, initialClass, new NetworkHook(), new RsaHook());
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