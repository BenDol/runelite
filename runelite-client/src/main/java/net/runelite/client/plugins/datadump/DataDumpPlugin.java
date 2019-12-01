/*
 * Copyright (c) 2018, James Swindle <wilingua@gmail.com>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.datadump;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.NPCManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.datadump.data.Position;
import net.runelite.client.plugins.datadump.npc.DummyNPC;
import net.runelite.client.plugins.datadump.npc.NpcSceneOverlay;
import net.runelite.client.plugins.datadump.npc.RemoveNullListSerializer;
import net.runelite.client.plugins.datadump.npc.Npc;
import net.runelite.client.plugins.datadump.object.GameItem;
import net.runelite.client.plugins.datadump.object.ObjectIndicatorsOverlay;
import net.runelite.client.plugins.datadump.object.GameObject;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
	name = "Data Dump",
	description = "",
	tags = {"data", "dump", "npcs", "overlay", "respawn", "objects"}
)
@Slf4j
public class DataDumpPlugin extends Plugin
{
	private static final String appName = RuneLiteProperties.getTitle();

	@Inject
	private Client client;

	@Inject
	private DataDumpConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NpcSceneOverlay npcSceneOverlay;

	@Inject
	private ObjectIndicatorsOverlay objectOverlay;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private NPCManager npcManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	/**
	 * NPCs to highlight
	 */
	@Getter(AccessLevel.PUBLIC)
	private final Set<NPC> highlightedNpcs = new HashSet<>();

	private final Set<DummyNPC> cachedNpcs = new CopyOnWriteArraySet<>();

	@Getter(AccessLevel.PUBLIC)
	private final List<TileObject> cachedObjects = new ArrayList<>();

	@Getter(AccessLevel.PUBLIC)
	private final List<ItemSpawned> cachedItems = new ArrayList<>();

	/**
	 * The time when the last game tick event ran.
	 */
	@Getter(AccessLevel.PACKAGE)
	private Instant lastTickUpdate;

	@Getter(AccessLevel.PUBLIC)
	private Map<Integer, Npc> npcs = new ConcurrentHashMap<>();
	private Map<Long, GameObject> gameObjects = new ConcurrentHashMap<>();
	private Map<Integer, List<GameItem>> gameItemsMap = new ConcurrentHashMap<>();

	private Gson gson = new Gson().newBuilder()
		.setPrettyPrinting()
		.registerTypeAdapter(List.class, new RemoveNullListSerializer())
		.create();

	private ScheduledFuture<?> npcDumpFuture, objectDumpFuture, itemDumpFeature;

	@Provides
	DataDumpConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DataDumpConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(npcSceneOverlay);
		overlayManager.add(objectOverlay);
		clientThread.invoke(this::rebuildAllNpcs);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(npcSceneOverlay);
		overlayManager.remove(objectOverlay);

		highlightedNpcs.clear();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) throws IOException {
		if (event.getGameState() == GameState.LOGIN_SCREEN ||
			event.getGameState() == GameState.HOPPING)
		{
			highlightedNpcs.clear();
		}

		if (event.getGameState() == GameState.LOGGED_IN) {
			if (config.dumpSpawnData()) {
				setupNpcDump();
			}
			if (config.dumpObjectData()) {
				setupObjectDump();
			}
			if (config.dumpItemData()) {
				setupItemDump();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged) throws IOException
	{
		if (!configChanged.getGroup().equals("datadump"))
		{
			return;
		}

		if (configChanged.getKey().equals("dumpSpawnData")) {
			if (Boolean.parseBoolean(configChanged.getNewValue())) {
				setupNpcDump();
			} else if (npcDumpFuture != null) {
				npcDumpFuture.cancel(false);
			}
		}

		if (configChanged.getKey().equals("dumpObjectData")) {
			if (Boolean.parseBoolean(configChanged.getNewValue())) {
				setupObjectDump();
			} else if (objectDumpFuture != null) {
				objectDumpFuture.cancel(false);
			}
		}

		if (configChanged.getKey().equals("dumpItemData")) {
			if (Boolean.parseBoolean(configChanged.getNewValue())) {
				setupItemDump();
			} else if (itemDumpFeature != null) {
				itemDumpFeature.cancel(false);
			}
		}

		rebuildAllNpcs();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		final NPC npc = npcSpawned.getNpc();
		final String npcName = npc.getName();

		if (npcName == null)
		{
			return;
		}

		memorizeNpc(npc);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		final NPC npc = npcDespawned.getNpc();

		highlightedNpcs.remove(npc);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		lastTickUpdate = Instant.now();
	}

	private void memorizeNpc(NPC npc)
	{
		if (!config.dumpSpawnData()) {
			return;
		}

		highlightedNpcs.add(npc);

		final int npcIndex = npc.getIndex();

		for (DummyNPC savedNpc : cachedNpcs) {
			if (savedNpc != null && savedNpc.getIndex() == npcIndex && savedNpc.getId() > 1) {
				return;
			}
		}

		cachedNpcs.add(new DummyNPC(npc, npcManager));
	}

	private void rebuildAllNpcs()
	{
		highlightedNpcs.clear();

		if (client.getGameState() != GameState.LOGGED_IN &&
			client.getGameState() != GameState.LOADING)
		{
			// NPCs are still in the client after logging out,
			// but we don't want to highlight those.
			return;
		}

		for (NPC npc : client.getNpcs())
		{
			final String npcName = npc.getName();

			if (npcName == null)
			{
				continue;
			}

			memorizeNpc(npc);
		}
	}

	private void setupNpcDump() throws IOException {
		final File file = new File(RuneLite.RUNELITE_DIR + "/npc-spawns.json");
		if (!file.exists()) {
			file.createNewFile();
		}

		if (npcDumpFuture != null) {
			npcDumpFuture.cancel(false);
		}

		npcDumpFuture = executor.scheduleWithFixedDelay(() -> {
			try {
				if (!cachedNpcs.isEmpty()) {
					InputStream is = null;
					try {
						is = new FileInputStream(file);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}

					npcs = gson.fromJson(new InputStreamReader(is), new TypeToken<Map<Integer, Npc>>() {}.getType());
					if (npcs == null) {
						npcs = new ConcurrentHashMap<>();
					}

					is.close();

					for (DummyNPC dummyNpc : cachedNpcs) {
						Npc cachedNpc = npcs.get(dummyNpc.getIndex());

						if (!dummyNpc.isDead() && (cachedNpc == null || cachedNpc.getId() < 0)) {
							npcs.put(dummyNpc.getIndex(), dummyNpc.getNpc());

							final String formattedMessage = new ChatMessageBuilder()
								.append(ChatColorType.HIGHLIGHT)
								.append(Color.BLUE, "[NPC]: " + dummyNpc.getNpc().toString())
								.build();

							chatMessageManager.queue(QueuedMessage.builder()
								.type(ChatMessageType.CONSOLE)
								.name(appName)
								.runeLiteFormattedMessage(formattedMessage)
								.build());
						}
					}

					OutputStream os = null;
					try {
						os = new FileOutputStream(file);
						os.write(gson.toJson(npcs).getBytes());
						os.close();
					} catch (IOException e) {
						e.printStackTrace();
						try {
							if (os != null) {
								os.close();
							}
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, 2, 10, TimeUnit.SECONDS);
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		if (config.dumpObjectData())
			cachedObjects.add(event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		if (config.dumpObjectData()) {
			final DecorativeObject eventObject = event.getDecorativeObject();
			cachedObjects.add(eventObject);
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (config.dumpObjectData())
			cachedObjects.add(event.getGameObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned groundObjectSpawned)
	{
		if (config.dumpObjectData()) {
			final GroundObject groundObject = groundObjectSpawned.getGroundObject();
			cachedObjects.add(groundObject);
		}
	}

	private void setupObjectDump() throws IOException {
		final File file = new File(RuneLite.RUNELITE_DIR + "/game-objects.json");
		if (!file.exists()) {
			file.createNewFile();
		}

		if (objectDumpFuture != null) {
			objectDumpFuture.cancel(false);
		}

		objectDumpFuture = executor.scheduleWithFixedDelay(() -> {
			try {
				if (!cachedObjects.isEmpty()) {
					InputStream is = null;
					try {
						is = new FileInputStream(file);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}

					gameObjects = gson.fromJson(new InputStreamReader(is), new TypeToken<Map<Long, GameObject>>() {}.getType());
					if (gameObjects == null) {
						gameObjects = new ConcurrentHashMap<>();
					}

					is.close();

					for (TileObject obj : cachedObjects) {
						if (!gameObjects.containsKey(obj.getHash())) {
							WorldPoint worldPoint = obj.getWorldLocation();
							int regionId = worldPoint.getRegionID();

							GameObject gameObject = new GameObject(obj.getId());
							gameObject.setRegion_id(regionId);
							gameObject.setPosition(new Position(worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane()));

							ObjectComposition composition = getObjectComposition(obj.getId());
							if (composition != null) {
								gameObject.setName(composition.getName());
								gameObject.setActions(new ArrayList<>(Arrays.asList(composition.getActions())));
							}

							gameObjects.put(obj.getHash(), gameObject);
						}
					}

					OutputStream os = null;
					try {
						os = new FileOutputStream(file);
						os.write(gson.toJson(gameObjects).getBytes());
						os.close();
					} catch (IOException e) {
						e.printStackTrace();
						try {
							if (os != null) {
								os.close();
							}
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, 2, 20, TimeUnit.SECONDS);
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned itemSpawned)
	{
		if (config.dumpItemData())
			cachedItems.add(itemSpawned);
	}

	private void setupItemDump() throws IOException {
		final File file = new File(RuneLite.RUNELITE_DIR + "/game-items.json");
		if (!file.exists()) {
			file.createNewFile();
		}

		if (itemDumpFeature != null) {
			itemDumpFeature.cancel(false);
		}

		itemDumpFeature = executor.scheduleWithFixedDelay(() -> {
			try {
				if (!cachedItems.isEmpty()) {
					InputStream is = null;
					try {
						is = new FileInputStream(file);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}

					gameItemsMap = gson.fromJson(new InputStreamReader(is), new TypeToken<Map<Integer, List<GameObject>>>() {}.getType());
					if (gameItemsMap == null) {
						gameItemsMap = new ConcurrentHashMap<>();
					}

					is.close();

					for (ItemSpawned itemSpawn : cachedItems) {
						Tile tile = itemSpawn.getTile();
						TileItem item = itemSpawn.getItem();
						WorldPoint worldPoint = tile.getWorldLocation();
						Position pos = new Position(worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane());

						if (!gameItemsMap.containsKey(pos.hashCode())) {
							GameItem gameItem = new GameItem(item.getId());
							gameItem.setRegion_id(worldPoint.getRegionID());
							gameItem.setPosition(pos);
							gameItem.setQuantity(item.getQuantity());

							ItemComposition composition = itemManager.getItemComposition(item.getId());
							gameItem.setName(composition.getName());

							List<GameItem> gameItems = gameItemsMap.computeIfAbsent(pos.hashCode(), k -> new ArrayList<>());
							gameItems.add(gameItem);
						}
					}

					OutputStream os = null;
					try {
						os = new FileOutputStream(file);
						os.write(gson.toJson(gameItemsMap).getBytes());
						os.close();
					} catch (IOException e) {
						e.printStackTrace();
						try {
							if (os != null) {
								os.close();
							}
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, 2, 20, TimeUnit.SECONDS);
	}



	private ObjectComposition getObjectComposition(int id)
	{
		ObjectComposition objectComposition = client.getObjectDefinition(id);
		return objectComposition.getImpostorIds() == null ? objectComposition : objectComposition.getImpostor();
	}
}
