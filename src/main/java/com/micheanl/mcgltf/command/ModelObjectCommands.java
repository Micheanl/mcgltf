package com.micheanl.mcgltf.command;

import com.micheanl.mcgltf.block.ModelBlockEntity;
import com.micheanl.mcgltf.block.ModelObjects;
import com.micheanl.mcgltf.config.EditorConfig;
import com.micheanl.mcgltf.entity.ModelEntity;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ModelObjectCommands {
	private static final SuggestionProvider<CommandSourceStack> MODELS = (context, builder) -> {
		Path dir = EditorConfig.directory();
		for (String name : EditorConfig.listModels()) {
			builder.suggest(name, new LiteralMessage(tooltip(dir.resolve(name), name)));
		}
		return builder.buildFuture();
	};

	private ModelObjectCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(Commands.literal("mcgltfobj")
						.then(Commands.literal("block")
								.then(Commands.argument("model", StringArgumentType.greedyString()).suggests(MODELS)
										.executes(ModelObjectCommands::placeBlock)))
						.then(Commands.literal("item")
								.then(Commands.argument("model", StringArgumentType.greedyString()).suggests(MODELS)
										.executes(ModelObjectCommands::giveItem)))
						.then(Commands.literal("entity")
								.then(Commands.argument("model", StringArgumentType.greedyString()).suggests(MODELS)
										.executes(ModelObjectCommands::spawnEntity)))));
	}

	private static int placeBlock(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		String model = StringArgumentType.getString(context, "model").trim();
		Path path = EditorConfig.directory().resolve(model);
		if (!Files.isRegularFile(path)) {
			source.sendFailure(Component.translatable("mcgltf.command.missing_model", model));
			return 0;
		}
		ServerPlayer player;
		try {
			player = source.getPlayerOrException();
		} catch (CommandSyntaxException e) {
			source.sendFailure(Component.translatable("mcgltf.command.needs_player"));
			return 0;
		}
		ServerLevel level = source.getLevel();
		BlockPos pos = player.blockPosition();
		level.setBlockAndUpdate(pos, ModelObjects.MODEL_BLOCK.defaultBlockState());
		BlockEntity entity = level.getBlockEntity(pos);
		if (!(entity instanceof ModelBlockEntity modelEntity)) {
			source.sendFailure(Component.translatable("mcgltf.command.place_fail"));
			return 0;
		}
		modelEntity.configure(model, 1.0f, 0.0f, 0.0f, 0.0f);
		String posStr = pos.getX() + " " + pos.getY() + " " + pos.getZ();
		source.sendSuccess(() -> Component.translatable("mcgltf.command.placed_block", model, posStr).withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int giveItem(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		String model = StringArgumentType.getString(context, "model").trim();
		Path path = EditorConfig.directory().resolve(model);
		if (!Files.isRegularFile(path)) {
			source.sendFailure(Component.translatable("mcgltf.command.missing_model", model));
			return 0;
		}
		ServerPlayer player;
		try {
			player = source.getPlayerOrException();
		} catch (CommandSyntaxException e) {
			source.sendFailure(Component.translatable("mcgltf.command.needs_player"));
			return 0;
		}
		ItemStack stack = new ItemStack(ModelObjects.MODEL_BLOCK_ITEM);
		stack.set(ModelObjects.MODEL_SOURCE, model);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(cleanName(model)));
		player.addItem(stack);
		source.sendSuccess(() -> Component.translatable("mcgltf.command.given_item", model).withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int spawnEntity(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		String model = StringArgumentType.getString(context, "model").trim();
		Path path = EditorConfig.directory().resolve(model);
		if (!Files.isRegularFile(path)) {
			source.sendFailure(Component.translatable("mcgltf.command.missing_model", model));
			return 0;
		}
		ServerPlayer player;
		try {
			player = source.getPlayerOrException();
		} catch (CommandSyntaxException e) {
			source.sendFailure(Component.translatable("mcgltf.command.needs_player"));
			return 0;
		}
		ServerLevel level = source.getLevel();
		ModelEntity entity = ModelObjects.MODEL_ENTITY.create(level, EntitySpawnReason.COMMAND);
		if (entity == null) {
			source.sendFailure(Component.translatable("mcgltf.command.spawn_fail"));
			return 0;
		}
		Vec3 pos = player.position();
		entity.snapTo(pos.x, pos.y, pos.z, player.getYRot(), 0.0f);
		entity.configure(model, 1.0f, 0.0f, 0.0f, 0.0f);
		level.addFreshEntity(entity);
		String posStr = String.format(Locale.ROOT, "@ %.1f %.1f %.1f", pos.x, pos.y, pos.z);
		source.sendSuccess(() -> Component.translatable("mcgltf.command.spawned_entity", model, posStr).withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static String cleanName(String model) {
		String base = model.substring(model.lastIndexOf('/') + 1);
		int dot = base.lastIndexOf('.');
		return dot > 0 ? base.substring(0, dot) : base;
	}

	private static String tooltip(Path path, String name) {
		String type = Component.translatable("mcgltf.command.glb_binary").getString();
		if (!name.toLowerCase(Locale.ROOT).endsWith(".glb")) {
			type = "glTF";
		}
		try {
			return type + " · " + formatSize(Files.size(path));
		} catch (IOException e) {
			return type;
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
}
