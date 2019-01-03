package fi.dy.masa.justenoughdimensions.client.render;

import java.util.function.Function;

import net.minecraftforge.fml.common.event.FMLInterModComms;

public class ShaderCheck implements Function<Boolean, Void> {
	private static boolean shaderOn = false;
	
	public static void init() {
		FMLInterModComms.sendFunctionMessage("unifine", "shadertest", "fi.dy.masa.justenoughdimensions.client.render.ShaderCheck");
	}
	
	@Override
	public Void apply(Boolean shader) {
		shaderOn = shader;
		return null;
	}
	
	public static boolean isShaderOn() {
		return shaderOn;
	}
}
