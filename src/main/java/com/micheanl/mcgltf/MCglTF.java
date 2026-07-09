package com.micheanl.mcgltf;

import com.micheanl.mcgltf.block.ModelObjects;
import com.micheanl.mcgltf.command.ModelObjectCommands;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

public class MCglTF implements ModInitializer {
	public static final String MOD_ID = "mcgltf";

	@Override
	public void onInitialize() {
		ModelObjects.register();
		ModelObjectCommands.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
