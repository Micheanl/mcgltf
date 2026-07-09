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
			source.sendError(Component.literal("模型不存在: " + name));
			return 0;
		}
		double x = source.getPlayer().getX();
		double y = source.getPlayer().getY();
		double z = source.getPlayer().getZ();
		source.sendFeedback(Component.literal("加载中: " + name));
		ModelLoader.loadAsync(path).whenComplete((result, error) ->
				source.getClient().execute(() -> placeResult(source, result, error, name, x, y, z, scale, 0.0f, 0.0f, 0.0f)));
		return 1;
	}

	private static void placeResult(FabricClientCommandSource source, LoadResult result, Throwable error,
			String name, double x, double y, double z, float scale, float rx, float ry, float rz) {
		if (error != null) {
			source.sendError(Component.literal("加载异常: " + error.getMessage()));
			return;
		}
		if (!(result instanceof LoadResult.Success ok)) {
			source.sendError(Component.literal("✘ 加载失败"));
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
			source.sendFeedback(Component.literal("✔ 已放置 " + ok.model().name() + " · 序号 " + (SceneRegistry.count() - 1)));
		} catch (RuntimeException e) {
			source.sendError(Component.literal("GPU 上传失败: " + e.getMessage()));
		}
	}

	private static int menu(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		source.sendFeedback(section("MCglTF 模型编辑器"));
		source.sendFeedback(Component.literal(" ")
				.append(button("[模型列表]", ChatFormatting.GREEN, "/mcgltf list"))
				.append(button(" [进入编辑]", ChatFormatting.AQUA, "/mcgltf edit"))
				.append(button(" [标记准星]", ChatFormatting.YELLOW, "/mcgltf pick"))
				.append(button(" [清空]", ChatFormatting.RED, "/mcgltf clear")));
		source.sendFeedback(Component.literal(" ")
				.append(openFileButton("[打开模型文件夹]", ChatFormatting.GOLD, EditorConfig.directory())));
		source.sendFeedback(hint(" 加载: /mcgltf load <模型>   放置对象: /mcgltfobj block|item|entity <模型>"));
		source.sendFeedback(hint(" 变换: 选中后 move/scale/rotate, 或 [编辑] 拖拽 gizmo (G/R/S 切模式)"));
		source.sendFeedback(hint(" 动画: /mcgltf anim <片段> · speed · seek · loop · crossfade"));
		source.sendFeedback(hint(" 场景: /mcgltf save <名> · open <名>"));
		return 1;
	}

	private static int listPanel(CommandContext<FabricClientCommandSource> context) {
		boolean shown = ListPanel.toggle();
		context.getSource().sendFeedback(Component.literal(shown
			? "模型面板已开启（打开聊天时显示在右下角）"
			: "模型面板已关闭").withStyle(shown ? ChatFormatting.GREEN : ChatFormatting.GRAY));
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
			source.sendError(Component.literal("序号无效: " + index));
			return 0;
		}
		source.getClient().execute(() -> SceneRegistry.remove(index));
		source.sendFeedback(Component.literal("已删除 [" + index + "]").withStyle(ChatFormatting.RED));
		return 1;
	}

	private static int here(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		SceneRegistry.Instance instance = SceneRegistry.selected();
		if (instance == null) {
			source.sendError(Component.literal("未选中模型, 用 /mcgltf select <序号>"));
			return 0;
		}
		double x = source.getPlayer().getX();
		double y = source.getPlayer().getY();
		double z = source.getPlayer().getZ();
		instance.position(x, y, z);
		source.sendFeedback(Component.literal(String.format(Locale.ROOT, "移到脚下 → %.2f %.2f %.2f", x, y, z)).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int toggleHud(CommandContext<FabricClientCommandSource> context) {
		EditorConfig.hud = !EditorConfig.hud;
		EditorConfig.save();
		context.getSource().sendFeedback(Component.literal("状态 HUD " + (EditorConfig.hud ? "已开启" : "已关闭"))
				.withStyle(EditorConfig.hud ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		return 1;
	}

	private static MutableComponent openFileButton(String label, ChatFormatting color, Path path) {
		Component hover = Component.literal(path.toString()).withStyle(ChatFormatting.GRAY);
		return Component.literal(label).withStyle(style -> style.withColor(color)
				.withClickEvent(new ClickEvent.OpenFile(path))
				.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	private static MutableComponent hint(String text) {
		return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
	}

	private static MutableComponent section(String title) {
		return Component.literal("▸ ").withStyle(ChatFormatting.DARK_AQUA)
				.append(Component.literal(title).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
	}


	private static MutableComponent button(String label, ChatFormatting color, String command) {
		return Component.literal(label).withStyle(style -> style.withColor(color).withClickEvent(new ClickEvent.RunCommand(command)));
	}

	private static int select(CommandContext<FabricClientCommandSource> context) {
		int index = IntegerArgumentType.getInteger(context, "index");
		if (!SceneRegistry.select(index)) {
			context.getSource().sendError(Component.literal("序号无效: " + index));
			return 0;
		}
		context.getSource().sendFeedback(Component.literal("已选中 [" + index + "] " + SceneRegistry.selected().source()));
		return 1;
	}

	private static int move(CommandContext<FabricClientCommandSource> context) {
		SceneRegistry.Instance instance = SceneRegistry.selected();
		if (instance == null) {
			context.getSource().sendError(Component.literal("未选中模型, 用 /mcgltf select <序号>"));
			return 0;
		}
		double x = DoubleArgumentType.getDouble(context, "x");
		double y = DoubleArgumentType.getDouble(context, "y");
		double z = DoubleArgumentType.getDouble(context, "z");
		instance.position(x, y, z);
		context.getSource().sendFeedback(Component.literal(String.format(Locale.ROOT, "位置 → %.2f %.2f %.2f", x, y, z)));
		return 1;
	}

	private static int scale(CommandContext<FabricClientCommandSource> context) {
		SceneRegistry.Instance instance = SceneRegistry.selected();
		if (instance == null) {
			context.getSource().sendError(Component.literal("未选中模型, 用 /mcgltf select <序号>"));
			return 0;
		}
		float value = FloatArgumentType.getFloat(context, "value");
		instance.scale(value);
		context.getSource().sendFeedback(Component.literal("缩放 → ×" + value));
		return 1;
	}

	private static int rotate(CommandContext<FabricClientCommandSource> context) {
		SceneRegistry.Instance instance = SceneRegistry.selected();
		if (instance == null) {
			context.getSource().sendError(Component.literal("未选中模型, 用 /mcgltf select <序号>"));
			return 0;
		}
		float x = FloatArgumentType.getFloat(context, "x");
		float y = FloatArgumentType.getFloat(context, "y");
		float z = FloatArgumentType.getFloat(context, "z");
		instance.rotation(x, y, z);
		context.getSource().sendFeedback(Component.literal(String.format(Locale.ROOT, "旋转 → %.1f° %.1f° %.1f°", x, y, z)));
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
			source.sendFeedback(Component.literal("✔ 场景已保存: " + name + " (" + instances.size() + " 个模型)"));
			return 1;
		} catch (IOException e) {
			source.sendError(Component.literal("保存失败: " + e.getMessage()));
			return 0;
		}
	}

	private static int openScene(CommandContext<FabricClientCommandSource> context) {
		String name = StringArgumentType.getString(context, "name");
		FabricClientCommandSource source = context.getSource();
		Path file = EditorConfig.directory().resolve(SCENES_DIR).resolve(name + ".json");
		if (!Files.isRegularFile(file)) {
			source.sendError(Component.literal("场景不存在: " + name));
			return 0;
		}
		Any root;
		try {
			root = JsonIterator.deserialize(Files.readAllBytes(file));
		} catch (IOException | RuntimeException e) {
			source.sendError(Component.literal("读取失败: " + e.getMessage()));
			return 0;
		}
		if (root == null || root.valueType() != ValueType.ARRAY) {
			source.sendError(Component.literal("场景格式无效"));
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
		source.sendFeedback(Component.literal("加载场景 " + name + " · " + loaded + " 个模型"));
		return 1;
	}

	private static int clear(CommandContext<FabricClientCommandSource> context) {
		context.getSource().getClient().execute(SceneRegistry::clear);
		context.getSource().sendFeedback(Component.literal("已清除全部实例"));
		return 1;
	}

	private static int edit(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		if (SceneRegistry.selected() == null) {
			source.sendError(Component.literal("未选中模型, 用 /mcgltf select <序号>"));
			return 0;
		}
		GizmoRenderer.show(GizmoRenderer.mode());
		source.getClient().execute(() -> source.getClient().gui.setScreen(new GizmoScreen()));
		source.sendFeedback(Component.literal("进入 gizmo 编辑 · 鼠标拖拽轴变换; G/R/S 切换 移动/旋转/缩放; Esc 退出"));
		return 1;
	}

	private static int resetModelDir(CommandContext<FabricClientCommandSource> context) {
		EditorConfig.modelDirectory = "";
		EditorConfig.save();
		context.getSource().sendFeedback(Component.literal("模型目录 → 默认 config/mcgltf/model ("
				+ EditorConfig.listModels().size() + " 个模型)"));
		return 1;
	}

	private static int modelDir(CommandContext<FabricClientCommandSource> context) {
		String path = StringArgumentType.getString(context, "path").trim();
		EditorConfig.modelDirectory = path;
		EditorConfig.save();
		List<String> files = EditorConfig.listModels();
		context.getSource().sendFeedback(Component.literal("模型目录 → "
				+ (path.isEmpty() ? "默认 config/mcgltf" : path) + " (" + files.size() + " 个模型)"));
		return 1;
	}

	private static int pick(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		Minecraft client = source.getClient();
		HitResult hit = client.hitResult;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ModelEntity model) {
			SceneRegistry.selectManaged(model.getId());
			source.sendFeedback(Component.literal("✔ 已标记模型生物 #" + model.getId()));
			return 1;
		}
		if (hit instanceof BlockHitResult blockHit && client.level != null
				&& client.level.getBlockState(blockHit.getBlockPos()).getBlock() == ModelObjects.MODEL_BLOCK) {
			SceneRegistry.selectManaged(blockHit.getBlockPos().immutable());
			source.sendFeedback(Component.literal("✔ 已标记模型方块 @ " + blockHit.getBlockPos().toShortString()));
			return 1;
		}
		SceneRegistry.selectManaged(null);
		source.sendFeedback(Component.literal("已清除标记（准星需对准模型方块/生物）"));
		return 1;
	}

	private static int gizmo(CommandContext<FabricClientCommandSource> context, GizmoRenderer.Mode mode) {
		FabricClientCommandSource source = context.getSource();
		if (SceneRegistry.selected() == null) {
			source.sendError(Component.literal("未选中模型, 用 /mcgltf select <序号>"));
			return 0;
		}
		GizmoRenderer.show(mode);
		source.sendFeedback(Component.literal(gizmoName(mode) + " gizmo 已显示"));
		return 1;
	}

	private static int gizmoOff(CommandContext<FabricClientCommandSource> context) {
		GizmoRenderer.hide();
		context.getSource().sendFeedback(Component.literal("gizmo 已隐藏"));
		return 1;
	}

	private static String gizmoName(GizmoRenderer.Mode mode) {
		return switch (mode) {
			case MOVE -> "移动";
			case ROTATE -> "旋转";
			case SCALE -> "缩放";
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
			source.sendError(Component.literal("未找到动画: " + clip));
			return 0;
		}
		source.sendFeedback(Component.literal("▶ 动画 '" + clip + "' 应用于 " + applied + " 个实例"));
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
			source.sendError(Component.literal("未找到动画: " + clip));
			return 0;
		}
		source.sendFeedback(Component.literal("⇄ 交叉淡入 '" + clip + "' " + seconds + "s · " + applied + " 个实例"));
		return 1;
	}

	private static int speed(CommandContext<FabricClientCommandSource> context) {
		float value = FloatArgumentType.getFloat(context, "value");
		forEachAnimator(animator -> animator.setSpeed(value));
		context.getSource().sendFeedback(Component.literal("速度 x" + value));
		return 1;
	}

	private static int seek(CommandContext<FabricClientCommandSource> context) {
		float value = FloatArgumentType.getFloat(context, "value");
		forEachAnimator(animator -> animator.seek(value));
		context.getSource().sendFeedback(Component.literal("seek " + value + "s"));
		return 1;
	}

	private static int playing(CommandContext<FabricClientCommandSource> context, boolean value) {
		forEachAnimator(animator -> animator.setPlaying(value));
		context.getSource().sendFeedback(Component.literal(value ? "▶ 继续" : "⏸ 暂停"));
		return 1;
	}

	private static int loop(CommandContext<FabricClientCommandSource> context) {
		Animator.Loop mode = switch (StringArgumentType.getString(context, "mode").toLowerCase(Locale.ROOT)) {
			case "once" -> Animator.Loop.ONCE;
			case "pingpong" -> Animator.Loop.PINGPONG;
			default -> Animator.Loop.LOOP;
		};
		forEachAnimator(animator -> animator.setLoop(mode));
		context.getSource().sendFeedback(Component.literal("循环模式 " + mode));
		return 1;
	}

	private static void forEachAnimator(Consumer<Animator> action) {
		for (SceneRegistry.Instance instance : SceneRegistry.all()) {
			action.accept(instance.animator());
		}
	}

	private static Message tooltip(Path path, String name) {
		String type = name.toLowerCase(Locale.ROOT).endsWith(".glb") ? "GLB 二进制" : "glTF";
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
				source.sendFeedback(Component.literal("… 其余 " + (issues.size() - MAX_REPORTED_ISSUES) + " 条省略"));
				return;
			}
			String prefix = issue.severity() == ValidationIssue.Severity.ERROR ? "✘ " : "⚠ ";
			source.sendFeedback(Component.literal(prefix + issue.message()));
		}
	}
}
