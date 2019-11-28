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

import static javassist.CtClass.intType;
import static net.runelite.client.RuneLite.GAME_RSA_MOD;

public class Client185ClassLoader extends ClientClassLoader {

	public Client185ClassLoader(URL[] urls, String initialClass) {
		super(urls, initialClass, new NetworkHook(), new RsaHook());
	}

	@SuppressWarnings({"unchecked"})
	static class NetworkHook extends net.runelite.client.rs.hook.NetworkHook {
		public NetworkHook() {
			super("ff");
		}

		@Override
		public void hook(ClassPool cp, CtClass cc) throws NotFoundException, CannotCompileException {
			CtClass object = cp.get(Object.class.getName());

			// Ctrl + F 'new Socket()' then search the parameter 'fw*.o ='
			hookGameHostAndPort(cc, new Pair[] {
				new Pair<>("vh", new Pair<>(new CtClass[]{cc, intType, intType, intType, object, intType}, new int[] {3, 5})),
				new Pair<>("nu", new Pair<>(new CtClass[]{cc, intType, intType, intType, object}, new int[] {3, 5})),
				new Pair<>("t",  new Pair<>(new CtClass[]{intType, intType, intType, object, intType}, new int[] {2, 4})),
				new Pair<>("i",  new Pair<>(new CtClass[]{intType, intType, intType, object}, new int[] {2, 4})),
				new Pair<>("o",  new Pair<>(new CtClass[]{intType, intType, intType, object}, new int[] {2, 4})),
				new Pair<>("c",  new Pair<>(new CtClass[]{intType, intType, intType, object}, new int[] {2, 4}))
			});
		}
	}

	static class RsaHook extends net.runelite.client.rs.hook.RsaHook {
		public RsaHook() {
			super("cn", "t");
		}
	}
}