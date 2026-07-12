package com.micheanl.mcgltf.client.command;

import com.jsoniter.JsonIterator;
import com.jsoniter.ValueType;
import com.jsoniter.any.Any;
import com.micheanl.mcgltf.animation.Animator;
import com.micheanl.mcgltf.asset.LoadResult;
import com.micheanl.mcgltf.asset.ModelLoader;
import com.micheanl.mcgltf.client.ui.GizmoScreen;
import com.micheanl.mcgltf.client.ui.ListPanel;
import com.micheanl.mcgltf.config.EditorConfig;
import com.micheanl.mcgltf.format.ValidationIssue;
import com.micheanl.mcgltf.render.GizmoRenderer;
import com.micheanl.mcgltf.render.GpuModel;
import com.micheanl.mcgltf.render.SceneRegistry;
import com.micheanl.mcgltf.scene.Model;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import com.micheanl.mcgltf.block.ModelObjects;
import com.micheanl.mcgltf.entity.ModelEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class ModelCommands {
	private static final int MAX_REPORTED_ISSUES = 8;
	private static final String SCENES_DIR = "scenes";

	private ModelCommands() {
	}

	private static final SuggestionProvider<FabricClientCommandSource> MODELS = (context, builder) -> {
		Path dir = EditorConfig.directory();
		for (String name : EditorConfig.listModels()) {
			builder.suggest(name, tooltip(dir.resolve(name), name));
		}
		return builder.buildFuture();
	};

	private static final SuggestionProvider<FabricClientCommandSource> SCENES = (context, builder) -> {
		for (String name : listFiles(EditorConfig.directory().resolve(SCENES_DIR), ".json")) {
			builder.suggest(name.substring(0, name.length() - ".json".length()));
		}
		return builder.buildFuture();
	};

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
				dispatcher.register(ClientCommands.literal("mcgltf")
						.executes(ModelCommands::menu)
						.then(ClientCommands.literal("load")
								.then(ClientCommands.argument("model", StringArgumentType.greedyString()).suggests(MODELS)
										.executes(context -> load(context, 1.0f))))
						.then(ClientCommands.literal("place")
								.then(ClientCommands.argument("scale", FloatArgumentType.floatArg(0.001f))
										.then(ClientCommands.argument("model", StringArgumentType.greedyString()).suggests(MODELS)
												.executes(context -> load(context, FloatArgumentType.getFloat(context, "scale"))))))
						.then(ClientCommands.literal("list").executes(ModelCommands::listPanel))
						.then(ClientCommands.literal("select")
								.then(ClientCommands.argument("index", IntegerArgumentType.integer(0)).executes(ModelCommands::select)))
						.then(ClientCommands.literal("delete")
								.then(ClientCommands.argument("index", IntegerArgumentType.integer(0)).executes(ModelCommands::delete)))
						.then(ClientCommands.literal("here").executes(ModelCommands::here))
						.then(ClientCommands.literal("hud").executes(ModelCommands::toggleHud))
						.then(ClientCommands.literal("move")
								.then(ClientCommands.argument("x", DoubleArgumentType.doubleArg())
										.then(ClientCommands.argument("y", DoubleArgumentType.doubleArg())
												.then(ClientCommands.argument("z", DoubleArgumentType.doubleArg())
														.executes(ModelCommands::move)))))
						.then(ClientCommands.literal("scale")
								.then(ClientCommands.argument("value", FloatArgumentType.floatArg(0.001f)).executes(ModelCommands::scale)))
						.then(ClientCommands.literal("rotate")
								.then(ClientCommands.argument("x", FloatArgumentType.floatArg())
										.then(ClientCommands.argument("y", FloatArgumentType.floatArg())
												.then(ClientCommands.argument("z", FloatArgumentType.floatArg())
														.executes(ModelCommands::rotate)))))
						.then(ClientCommands.literal("save")
								.then(ClientCommands.argument("name", StringArgumentType.word()).executes(ModelCommands::saveScene)))
						.then(ClientCommands.literal("open")
								.then(ClientCommands.argument("name", StringArgumentType.word()).suggests(SCENES).executes(ModelCommands::openScene)))
						.then(ClientCommands.literal("clear").executes(ModelCommands::clear))
						.then(ClientCommands.literal("edit").executes(ModelCommands::edit))
						.then(ClientCommands.literal("pick").executes(ModelCommands::pick))
						.then(ClientCommands.literal("modeldir")
								.executes(ModelCommands::resetModelDir)
								.then(ClientCommands.argument("path", StringArgumentType.greedyString()).executes(ModelCommands::modelDir)))
						.then(ClientCommands.literal("gizmo")
								.then(ClientCommands.literal("move").executes(context -> gizmo(context, GizmoRenderer.Mode.MOVE)))
								.then(ClientCommands.literal("rotate").executes(context -> gizmo(context, GizmoRenderer.Mode.ROTATE)))
								.then(ClientCommands.literal("scale").executes(context -> gizmo(context, GizmoRenderer.Mode.SCALE)))
								.then(ClientCommands.literal("off").executes(ModelCommands::gizmoOff)))
						.then(ClientCommands.literal("anim")
								.then(ClientCommands.argument("clip", StringArgumentType.greedyString()).executes(ModelCommands::anim)))
						.then(ClientCommands.literal("speed")
								.then(ClientCommands.argument("value", FloatArgumentType.floatArg(0.0f)).executes(ModelCommands::speed)))
						.then(ClientCommands.literal("seek")
								.then(ClientCommands.argument("value", FloatArgumentType.floatArg(0.0f)).executes(ModelCommands::seek)))
						.then(ClientCommands.literal("pause").executes(context -> playing(context, false)))
						.then(ClientCommands.literal("resume").executes(context -> playing(context, true)))
						.then(ClientCommands.literal("loop")
								.then(ClientCommands.argument("mode", StringArgumentType.word()).executes(ModelCommands::loop)))
						.then(ClientCommands.literal("crossfade")
								.then(ClientCommands.argument("seconds", FloatArgumentType.floatArg(0.0f))
										.then(ClientCommands.argument("clip", StringArgumentType.greedyString()).executes(ModelCommands::crossfade))))));
	}

	private static int load(CommandContext<FabricClientCommandSource> context, float scale) {
		String name = StringArgumentType.getString(context, "model").trim();
		FabricClientCommandSource source = context.getSource();
		Path path = EditorConfig.directory().resolve(name);
		if (!Files.isRegularFile(path)) {
			source.sendError(Component.translatable("mcgltf.command.missing_model", name));
			return 0;
		}
		double x = source.getPlayer().getX();
		double y = source.getPlayer().getY();
		double z = source.getPlayer().getZ();
		source.sendFeedback(Component.translatable("mcgltf.command.loading", name));
		ModelLoader.loadAsync(path).whenComplete((result, error) ->
				source.getClient().execute(() -> placeResult(source, result, error, name, x, y, z, scale, 0.0f, 0.0f, 0.0f)));
		return 1;
	}

	private static void placeResult(FabricClientCommandSource source, LoadResult result, Throwable error,
			String name, double x, double y, double z, float scale, float rx, float ry, float rz) {
		if (error != null) {
			source.sendError(Component.translatable("mcgltf.command.load_exception", error.getMessage()));
			return;
		}
		if (!(result instanceof LoadResult.Success ok)) {
			source.sendError(Component.translatable("mcgltf.command.load_fail"));
			if (result instanceof LoadResult.Failure failed) {
				reportIssues(source, failed.issues());
			}
			return;
		}
		try {
			GpuModel model = GpuModel.upload(ok.model());
			SceneRegistry.Instance placed = new SceneRegistry.Instance(model, name, x, y, z, scale, new Animator(model.model()));
			placed.rotation(rx, ry, rz);
			SceneRegistry.add(placed);
			source.sendFeedback(Component.translatable("mcgltf.command.placed", ok.model().name(), SceneRegistry.count() - 1));
		} catch (RuntimeException e) {
			source.sendError(Component.translatable("mcgltf.command.gpu_upload_fail", e.getMessage()));
		}
	}

	private static int menu(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		source.sendFeedback(section(Component.translatable("mcgltf.command.editor_title")));
		source.sendFeedback(Component.literal(" ")
				.append(button(Component.translatable("mcgltf.command.model_list"), ChatFormatting.GREEN, "/mcgltf list"))
				.append(button(Component.translatable("mcgltf.command.enter_edit"), ChatFormatting.AQUA, "/mcgltf edit"))
				.append(button(Component.translatable("mcgltf.command.pick"), ChatFormatting.YELLOW, "/mcgltf pick"))
				.append(button(Component.translatable("mcgltf.command.clear"), ChatFormatting.RED, "/mcgltf clear")));
		source.sendFeedback(Component.literal(" ")
				.append(openFileButton(Component.translatable("mcgltf.command.open_model_dir"), ChatFormatting.GOLD, EditorConfig.directory())));
		source.sendFeedback(hint(Component.translatable("mcgltf.command.help_load")));
		source.sendFeedback(hint(Component.translatable("mcgltf.command.help_transform")));
		source.sendFeedback(hint(Component.translatable("mcgltf.command.help_anim")));
		source.sendFeedback(hint(Component.translatable("mcgltf.command.help_scene")));
		return 1;
	}

	private static int listPanel(CommandContext<FabricClientCommandSource> context) {
		boolean shown = ListPanel.toggle();
		context.getSource().sendFeedback((shown
			? Component.translatable("mcgltf.command.panel_on")
			: Component.translatable("mcgltf.command.panel_off")).withStyle(shown ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		return 1;
	}

	public static void loadModel(String name, float scale) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		Path path = EditorConfig.directory().resolve(name);
		if (!Files.isRegularFile(path)) {
			return;
		}
		double x = minecraft.player.getX();
		double y = minecraft.player.getY();
		double z = minecraft.player.getZ();
		ModelLoader.loadAsync(path).whenComplete((result, error) ->
			minecraft.execute(() -> placeQuiet(result, name, x, y, z, scale)));
	}

	private static void placeQuiet(LoadResult result, String name, double x, double y, double z, float scale) {
		if (!(result instanceof LoadResult.Success ok)) {
			return;
		}
		try {
			GpuModel model = GpuModel.upload(ok.model());
			SceneRegistry.add(new SceneRegistry.Instance(model, name, x, y, z, scale, new Animator(model.model())));
		} catch (RuntimeException ignored) {
		}
	}

	private static int delete(CommandContext<FabricClientCommandSource> context) {
		int index = IntegerArgumentType.getInteger(context, "index");
		FabricClientCommandSource source = context.getSource();
		if (index < 0 || index >= SceneRegistry.count()) {
			source.sendError(Component.translatable("mcgltf.command.invalid_index", index));
			return 0;
		}
		source.getClient().execute(() -> SceneRegistry.remove(index));
		source.sendFeedback(Component.translatable("mcgltf.command.deleted_item", index).withStyle(ChatFormatting.RED));
		return 1;
	}

	private static int here(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		SceneRegistry.Instance instance = SceneRegistry.selected();
		if (instance == null) {
			source.sendError(Component.translatable("mcgltf.command.not_selected"));
			return 0;
		}
		double x = source.getPlayer().getX();
		double y = source.getPlayer().getY();
		double z = source.getPlayer().getZ();
		instance.position(x, y, z);
		source.sendFeedback(Component.translatable("mcgltf.command.moved_here", x, y, z).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int toggleHud(CommandContext<FabricClientCommandSource> context) {
		EditorConfig.hud = !EditorConfig.hud;
		EditorConfig.save();
		context.getSource().sendFeedback(EditorConfig.hud ? Component.translatable("mcgltf.command.hud_on") : Component.translatable("mcgltf.command.hud_off")
				.withStyle(EditorConfig.hud ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		return 1;
	}

	private static MutableComponent openFileButton(Component label, ChatFormatting color, Path path) {
		Component hover = Component.literal(path.toString()).withStyle(ChatFormatting.GRAY);
		return label.copy().withStyle(style -> style.withColor(color)
				.withClickEvent(new ClickEvent.OpenFile(path))
				.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	private static MutableComponent hint(Component text) {
		return text.copy().withStyle(ChatFormatting.DARK_GRAY);
	}

	private static MutableComponent section(Component title) {
		return Component.literal("▸ ").withStyle(ChatFormatting.DARK_AQUA)
				.append(title.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
	}


	private static MutableComponent button(Component label, ChatFormatting color, String command) {
		return label.copy().withStyle(style -> style.withColor(color).withClickEvent(new ClickEvent.RunCommand(command)));
	}

	private static int select(CommandContext<FabricClientCommandSource> context) {
		int index = IntegerArgumentType.getInteger(context, "index");
		if (!SceneRegistry.select(index)) {
			context.getSource().sendError(Component.translatable("mcgltf.command.invalid_index", index));
			return 0;
		}
		context.getSource().sendFeedback(Component.translatable("mcgltf.command.selected_item", index, SceneRegistry.selected().source()));
		return 1;
	}

	private static int move(CommandContext<FabricClientCommandSource> context) {
		SceneRegistry.Instance instance = SceneRegistry.selected();
		if (instance == null) {
			context.getSource().sendError(Component.translatable("mcgltf.command.not_selected"));
			return 0;
		}
		double x = DoubleArgumentType.getDouble(context, "x");
		double y = DoubleArgumentType.getDouble(context, "y");
		double z = DoubleArgumentType.getDouble(context, "z");
		instance.position(x, y, z);
		context.getSource().sendFeedback(Component.translatable("mcgltf.command.moved_to", x, y, z));
		return 1;
	}

	private static int scale(CommandContext<FabricClientCommandSource> context) {
		SceneRegistry.Instance instance = SceneRegistry.selected();
		if (instance == null) {
			context.getSource().sendError(Component.translatable("mcgltf.command.not_selected"));
			return 0;
		}
		float value = FloatArgumentType.getFloat(context, "value");
		instance.scale(value);
		context.getSource().sendFeedback(Component.translatable("mcgltf.command.scaled_to", value));
		return 1;
	}

	private static int rotate(CommandContext<FabricClientCommandSource> context) {
		SceneRegistry.Instance instance = SceneRegistry.selected();
		if (instance == null) {
			context.getSource().sendError(Component.translatable("mcgltf.command.not_selected"));
			return 0;
		}
		float x = FloatArgumentType.getFloat(context, "x");
		float y = FloatArgumentType.getFloat(context, "y");
		float z = FloatArgumentType.getFloat(context, "z");
		instance.rotation(x, y, z);
		context.getSource().sendFeedback(Component.translatable("mcgltf.command.rotated_to", x, y, z));
		return 1;
	}

	private static int saveScene(CommandContext<FabricClientCommandSource> context) {
		String name = StringArgumentType.getString(context, "name");
		FabricClientCommandSource source = context.getSource();
		StringBuilder json = new StringBuilder("[\n");
		List<SceneRegistry.Instance> instances = SceneRegistry.all();
		for (int i = 0; i < instances.size(); i++) {
			SceneRegistry.Instance instance = instances.get(i);
			json.append("\t{\"source\": \"").append(escape(instance.source())).append("\", ")
					.append("\"x\": ").append(instance.x()).append(", ")
					.append("\"y\": ").append(instance.y()).append(", ")
					.append("\"z\": ").append(instance.z()).append(", ")
					.append("\"scale\": ").append(instance.scale()).append(", ")
					.append("\"rx\": ").append(instance.rotationX()).append(", ")
					.append("\"ry\": ").append(instance.rotationY()).append(", ")
					.append("\"rz\": ").append(instance.rotationZ()).append("}")
					.append(i < instances.size() - 1 ? ",\n" : "\n");
		}
		json.append("]\n");
		try {
			Path dir = EditorConfig.directory().resolve(SCENES_DIR);
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(name + ".json"), json.toString());
			source.sendFeedback(Component.translatable("mcgltf.command.scene_saved", name, instances.size()));
			return 1;
		} catch (IOException e) {
			source.sendError(Component.translatable("mcgltf.command.save_fail", e.getMessage()));
			return 0;
		}
	}

	private static int openScene(CommandContext<FabricClientCommandSource> context) {
		String name = StringArgumentType.getString(context, "name");
		FabricClientCommandSource source = context.getSource();
		Path file = EditorConfig.directory().resolve(SCENES_DIR).resolve(name + ".json");
		if (!Files.isRegularFile(file)) {
			source.sendError(Component.translatable("mcgltf.command.scene_not_found", name));
			return 0;
		}
		Any root;
		try {
			root = JsonIterator.deserialize(Files.readAllBytes(file));
		} catch (IOException | RuntimeException e) {
			source.sendError(Component.translatable("mcgltf.command.read_fail", e.getMessage()));
			return 0;
		}
		if (root == null || root.valueType() != ValueType.ARRAY) {
			source.sendError(Component.translatable("mcgltf.command.invalid_scene"));
			return 0;
		}
		SceneRegistry.clear();
		int loaded = 0;
		for (Any entry : root.asList()) {
			String modelName = entry.get("source").toString();
			double x = entry.get("x").toDouble();
			double y = entry.get("y").toDouble();
			double z = entry.get("z").toDouble();
			float scale = entry.get("scale").toFloat();
			float rx = entry.get("rx").toFloat();
			float ry = entry.get("ry").toFloat();
			float rz = entry.get("rz").toFloat();
			Path path = EditorConfig.directory().resolve(modelName);
			if (!Files.isRegularFile(path)) {
				continue;
			}
			loaded++;
			ModelLoader.loadAsync(path).whenComplete((result, error) ->
					source.getClient().execute(() -> placeResult(source, result, error, modelName, x, y, z, scale, rx, ry, rz)));
		}
		source.sendFeedback(Component.translatable("mcgltf.command.scene_loaded", name, loaded));
		return 1;
	}

	private static int clear(CommandContext<FabricClientCommandSource> context) {
		context.getSource().getClient().execute(SceneRegistry::clear);
		context.getSource().sendFeedback(Component.translatable("mcgltf.command.cleared"));
		return 1;
	}

	private static int edit(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		if (SceneRegistry.selected() == null) {
			source.sendError(Component.translatable("mcgltf.command.not_selected"));
			return 0;
		}
		GizmoRenderer.show(GizmoRenderer.mode());
		source.getClient().execute(() -> source.getClient().gui.setScreen(new GizmoScreen()));
		source.sendFeedback(Component.translatable("mcgltf.command.gizmo_enter"));
		return 1;
	}

	private static int resetModelDir(CommandContext<FabricClientCommandSource> context) {
		EditorConfig.modelDirectory = "";
		EditorConfig.save();
		context.getSource().sendFeedback(Component.translatable("mcgltf.command.model_dir_default", EditorConfig.listModels().size()));
		return 1;
	}

	private static int modelDir(CommandContext<FabricClientCommandSource> context) {
		String path = StringArgumentType.getString(context, "path").trim();
		EditorConfig.modelDirectory = path;
		EditorConfig.save();
		List<String> files = EditorConfig.listModels();
		context.getSource().sendFeedback(Component.translatable("mcgltf.command.model_dir", path.isEmpty() ? "config/mcgltf" : path, files.size()));
		return 1;
	}

	private static int pick(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		Minecraft client = source.getClient();
		HitResult hit = client.hitResult;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ModelEntity model) {
			SceneRegistry.selectManaged(model.getId());
			source.sendFeedback(Component.translatable("mcgltf.command.picked_entity", model.getId()));
			return 1;
		}
		if (hit instanceof BlockHitResult blockHit && client.level != null
				&& client.level.getBlockState(blockHit.getBlockPos()).getBlock() == ModelObjects.MODEL_BLOCK) {
			SceneRegistry.selectManaged(blockHit.getBlockPos().immutable());
			source.sendFeedback(Component.translatable("mcgltf.command.picked_block", blockHit.getBlockPos().toShortString()));
			return 1;
		}
		SceneRegistry.selectManaged(null);
		source.sendFeedback(Component.translatable("mcgltf.command.clear_pick"));
		return 1;
	}

	private static int gizmo(CommandContext<FabricClientCommandSource> context, GizmoRenderer.Mode mode) {
		FabricClientCommandSource source = context.getSource();
		if (SceneRegistry.selected() == null) {
			source.sendError(Component.translatable("mcgltf.command.not_selected"));
			return 0;
		}
		GizmoRenderer.show(mode);
		source.sendFeedback(Component.translatable("mcgltf.command.gizmo_shown", gizmoName(mode)));
		return 1;
	}

	private static int gizmoOff(CommandContext<FabricClientCommandSource> context) {
		GizmoRenderer.hide();
		context.getSource().sendFeedback(Component.translatable("mcgltf.command.gizmo_hidden"));
		return 1;
	}

	private static String gizmoName(GizmoRenderer.Mode mode) {
		return switch (mode) {
			case MOVE -> Component.translatable("mcgltf.hud.move").getString();
			case ROTATE -> Component.translatable("mcgltf.hud.rotate").getString();
			case SCALE -> Component.translatable("mcgltf.hud.scale").getString();
		};
	}

	private static int anim(CommandContext<FabricClientCommandSource> context) {
		String clip = StringArgumentType.getString(context, "clip").trim();
		FabricClientCommandSource source = context.getSource();
		int applied = 0;
		for (SceneRegistry.Instance instance : SceneRegistry.all()) {
			int index = instance.animator().clipIndex(clip);
			if (index >= 0 && instance.animator().play(index)) {
				applied++;
			}
		}
		if (applied == 0) {
			source.sendError(Component.translatable("mcgltf.command.anim_not_found", clip));
			return 0;
		}
		source.sendFeedback(Component.translatable("mcgltf.command.anim_play", clip, applied));
		return 1;
	}

	private static int crossfade(CommandContext<FabricClientCommandSource> context) {
		float seconds = FloatArgumentType.getFloat(context, "seconds");
		String clip = StringArgumentType.getString(context, "clip").trim();
		FabricClientCommandSource source = context.getSource();
		int applied = 0;
		for (SceneRegistry.Instance instance : SceneRegistry.all()) {
			int index = instance.animator().clipIndex(clip);
			if (index >= 0 && instance.animator().crossfade(index, seconds)) {
				applied++;
			}
		}
		if (applied == 0) {
			source.sendError(Component.translatable("mcgltf.command.anim_not_found", clip));
			return 0;
		}
		source.sendFeedback(Component.translatable("mcgltf.command.anim_crossfade", clip, seconds, applied));
		return 1;
	}

	private static int speed(CommandContext<FabricClientCommandSource> context) {
		float value = FloatArgumentType.getFloat(context, "value");
		forEachAnimator(animator -> animator.setSpeed(value));
		context.getSource().sendFeedback(Component.translatable("mcgltf.command.anim_speed", value));
		return 1;
	}

	private static int seek(CommandContext<FabricClientCommandSource> context) {
		float value = FloatArgumentType.getFloat(context, "value");
		forEachAnimator(animator -> animator.seek(value));
		context.getSource().sendFeedback(Component.translatable("mcgltf.command.anim_seek", value));
		return 1;
	}

	private static int playing(CommandContext<FabricClientCommandSource> context, boolean value) {
		forEachAnimator(animator -> animator.setPlaying(value));
		context.getSource().sendFeedback(value ? Component.translatable("mcgltf.command.anim_resume") : Component.translatable("mcgltf.command.anim_pause"));
		return 1;
	}

	private static int loop(CommandContext<FabricClientCommandSource> context) {
		Animator.Loop mode = switch (StringArgumentType.getString(context, "mode").toLowerCase(Locale.ROOT)) {
			case "once" -> Animator.Loop.ONCE;
			case "pingpong" -> Animator.Loop.PINGPONG;
			default -> Animator.Loop.LOOP;
		};
		forEachAnimator(animator -> animator.setLoop(mode));
		context.getSource().sendFeedback(Component.translatable("mcgltf.command.anim_loop", mode));
		return 1;
	}

	private static void forEachAnimator(Consumer<Animator> action) {
		for (SceneRegistry.Instance instance : SceneRegistry.all()) {
			action.accept(instance.animator());
		}
	}

	private static Message tooltip(Path path, String name) {
		String type = name.toLowerCase(Locale.ROOT).endsWith(".glb") ? Component.translatable("mcgltf.command.glb_binary").getString() : "glTF";
		try {
			return new LiteralMessage(type + " · " + formatSize(Files.size(path)));
		} catch (IOException e) {
			return new LiteralMessage(type);
		}
	}

	private static String formatSize(long bytes) {
		if (bytes >= 1024L * 1024L) {
			return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
		}
		if (bytes >= 1024L) {
			return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
		}
		return bytes + " B";
	}

	private static List<String> listFiles(Path dir, String... extensions) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.map(path -> path.getFileName().toString())
					.filter(name -> {
						String lower = name.toLowerCase(Locale.ROOT);
						for (String extension : extensions) {
							if (lower.endsWith(extension)) {
								return true;
							}
						}
						return false;
					})
					.sorted()
					.toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static void reportIssues(FabricClientCommandSource source, List<ValidationIssue> issues) {
		int shown = 0;
		for (ValidationIssue issue : issues) {
			if (shown++ >= MAX_REPORTED_ISSUES) {
				source.sendFeedback(Component.translatable("mcgltf.command.issues_omitted", issues.size() - MAX_REPORTED_ISSUES));
				return;
			}
			String prefix = issue.severity() == ValidationIssue.Severity.ERROR ? "✘ " : "⚠ ";
			source.sendFeedback(Component.literal(prefix + issue.message()));
		}
	}
}
