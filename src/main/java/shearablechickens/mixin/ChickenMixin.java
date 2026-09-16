package shearablechickens.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import shearablechickens.ShearableChickens;

@Mixin(Chicken.class)
public abstract class ChickenMixin extends Animal implements Shearable {
	private ChickenMixin(final EntityType<? extends Animal> type, final Level level) {
		super(type, level);
	}

	private static final AttachmentType<Boolean> IS_SHEARED = AttachmentRegistry.createPersistent(
		ShearableChickens.id("is_sheared"),
		Codec.BOOL
	);

	boolean isSheared() {
		return this.getAttachedOrElse(IS_SHEARED, false);
	}

	void setSheared() {
		this.setAttached(IS_SHEARED, true);
	}

	private static final int SHEAR_PARTICLE_DURATION = 20;
	private int shearParticleTimer = 0;

	// Ensure eggTime never reaches 0 if chicken has been sheared
	@Redirect(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/animal/chicken/Chicken;eggTime:I", opcode = Opcodes.GETFIELD))
	public int getEggTime(Chicken instance) {
		return this.isSheared() ? 1000 : instance.eggTime;
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void onAiStep(CallbackInfo ci) {
		if (this.isSheared() && this.shearParticleTimer > 0) {
			if (shearParticleTimer % 4 == 0 && this.level() instanceof ServerLevel level) {
				// Mimic golden dandelion particles
				var position = new Vec3(this.getRandomX(1.25), this.getRandomY(0.33) + this.getBbHeight() + 0.2F, this.getRandomZ(1.25));
				level.sendParticles(ParticleTypes.PAUSE_MOB_GROWTH, position.x, position.y, position.z, 1, 0, 0, 0, 0);
			}

			shearParticleTimer--;
		}
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack itemStack = player.getItemInHand(hand);
		if (itemStack.is(Items.SHEARS)) {
			if (this.level() instanceof ServerLevel level && this.readyForShearing()) {
				this.shear(level, SoundSource.PLAYERS, itemStack);
				this.gameEvent(GameEvent.SHEAR, player);
				itemStack.hurtAndBreak(1, player, hand.asEquipmentSlot());
				return InteractionResult.SUCCESS_SERVER;
			} else {
				return InteractionResult.CONSUME;
			}
		} else {
			return super.mobInteract(player, hand);
		}
	}

	@Override
	public void shear(final ServerLevel level, final SoundSource soundSource, final ItemStack tool) {
		level.playSound(null, this, SoundEvents.SHEEP_SHEAR, soundSource, 1.0F, 1.0F);
		shearParticleTimer = SHEAR_PARTICLE_DURATION;
		this.setSheared();
	}

	@Override
	public boolean readyForShearing() {
		return !this.isSheared();
	}
}
