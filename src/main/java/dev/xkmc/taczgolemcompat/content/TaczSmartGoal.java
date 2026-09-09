package dev.xkmc.taczgolemcompat.content;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import dev.xkmc.mob_weapon_api.api.ai.IWeaponHolder;
import dev.xkmc.mob_weapon_api.api.goals.IMeleeGoal;
import dev.xkmc.mob_weapon_api.api.goals.IRangedWeaponGoal;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

import static dev.xkmc.taczgolemcompat.content.TaczSmartGoal.GunState.*;

/**
 * Adapted from Pillager's Gun.
 * <p>
 * mob_weapon_api 3.x made {@code SmartRangedAttackGoal}'s constructor protected,
 * so this goal no longer extends it and instead implements {@link IRangedWeaponGoal}
 * directly, copying the strafing logic from mob_weapon_api 3.0.20.
 */
public class TaczSmartGoal<T extends Mob> extends Goal implements IRangedWeaponGoal<T> {

	protected final T mob;
	protected final IWeaponHolder holder;
	protected final IMeleeGoal melee;
	protected final double speedModifier;
	protected final double radius;
	protected int seeTime;
	private boolean strafingClockwise;
	private boolean strafingBackwards;
	private int strafingTime = -1;

	private GunState gunState;
	private int attackDelay;
	private double attackCount;
	private int ammoCount;

	public TaczSmartGoal(T mob, IWeaponHolder holder, IMeleeGoal melee, double speed, double radius) {
		this.mob = mob;
		this.holder = holder;
		this.melee = melee;
		this.speedModifier = speed;
		this.radius = radius;
		this.gunState = UNCHARGED;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	public boolean canUse() {
		return isHoldingGun() && (isValidTarget() || !hasAmmo() && canReload() || gunState == CHARGING);
	}

	public boolean canContinueToUse() {
		return isHoldingGun() && (isValidTarget() || !hasAmmo() && canReload() || gunState == CHARGING);
	}

	public void start() {
		super.start();
		this.gunState = UNCHARGED;
		var operator = IGunOperator.fromLivingEntity(mob);
		operator.draw(mob::getMainHandItem);
		operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10000L;
	}

	public void stop() {
		super.stop();
		attackDelay = 0;
		attackCount = 0;
		mob.getNavigation().stop();
		var gun = getGun();
		if (gun != null && isHoldingGun()) {
			IGunOperator operator = IGunOperator.fromLivingEntity(mob);
			operator.cancelReload();
			operator.aim(false);
			GunData gunData = TimelessAPI.getCommonGunIndex(gun.getGunId(mob.getMainHandItem())).map(CommonGunIndex::getGunData).orElse(null);
			if (gunData != null && gunData.getBolt() == Bolt.MANUAL_ACTION) {
				operator.bolt();
			}
		}

	}

	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public void tick() {
		LivingEntity target = mob.getTarget();
		var gun = getGun();
		ItemStack stack = mob.getMainHandItem();
		IGunOperator operator = IGunOperator.fromLivingEntity(mob);
		boolean preloadOnly = false;
		if (gun == null || !isHoldingGun()) return;
		if (target != null) {
			double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
			double dist = target.position().subtract(mob.position()).length();
			preloadOnly = isValidTarget() && (dist > range || seeTime < 5) && attackDelay == 0;
			if (isValidTarget() && canMeleeAttack(operator, target)) {
				operator.melee();
				return;
			}
			strafing();
			mob.getLookControl().setLookAt(target, 30, 90);
		}
		if (gun.isOverheatLocked(stack)) {
			gunState = UNCHARGED;
			return;
		}
		if (gunState == UNCHARGED && hasAmmo()) {
			gunState = CHARGED;
			ammoCount = getAmmoCount(stack);
		} else if (gunState == CHARGED && !hasAmmo()) {
			gunState = UNCHARGED;
		}
		if (gunState == UNCHARGED) {
			if (!preloadOnly && canReload()) {
				operator.reload();
				gunState = CHARGING;
				if (mob instanceof CrossbowAttackMob cs) {
					cs.setChargingCrossbow(true);
				}
			}
		} else if (gunState == CHARGING) {
			if (!isHoldingGun()) {
				gunState = UNCHARGED;
			}
			if (!operator.getDataHolder().reloadStateType.isReloading()) {
				if (hasAmmo()) {
					gunState = CHARGED;
					attackDelay = 10 + mob.getRandom().nextInt(20);
					ammoCount = getAmmoCount(stack);
				} else {
					gunState = UNCHARGED;
				}
				if (mob instanceof CrossbowAttackMob cs) {
					cs.setChargingCrossbow(false);
				}
			}
		} else if (gunState == CHARGED) {
			if (--attackDelay <= 0) {
				operator.aim(true);
				gunState = READY_TO_ATTACK;
				attackDelay = 0;
			}
		} else if (gunState == READY_TO_ATTACK && seeTime > 0 && isRightAngle()) {
			GunData data = TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(CommonGunIndex::getGunData).orElse(null);
			if (data != null) {
				for (attackCount += gun.getFireMode(stack) == FireMode.AUTO ? gun.getRPM(stack) / 1200d : gun.getRPM(stack) / (Math.max(1200 * (target.distanceTo(mob) / 8), 2400)); attackCount >= 1; --attackCount) {
					if (isHoldingGun()) {
						operator.shoot(mob::getXRot, mob::getYHeadRot);
						if (data.getBolt() == Bolt.MANUAL_ACTION) {
							operator.bolt();
						}

						if (ammoCount > 0) {
							--ammoCount;
						}

						if (ammoCount == 0) {
							operator.aim(false);
							attackCount = 0;
							gunState = UNCHARGED;
							break;
						}
					}
				}
			}
		}

	}

	@Override
	public double range(ItemStack stack) {
		return radius;
	}

	@Override
	public void performRangedAttack(LivingEntity livingEntity, float v, ItemStack itemStack, InteractionHand interactionHand) {
	}

	protected void strafing() {
		var target = mob.getTarget();
		if (target == null) return;
		double dist = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
		boolean sight = mob.getSensing().hasLineOfSight(target);
		boolean oldSight = seeTime > 0;
		if (sight != oldSight) {
			seeTime = 0;
		}
		if (sight) {
			++seeTime;
		} else {
			--seeTime;
		}
		double sqr = attackRadiusSqr();
		if (dist <= sqr && seeTime >= 20) {
			mob.getNavigation().stop();
			++strafingTime;
		} else {
			mob.getNavigation().moveTo(target, speedModifier);
			strafingTime = -1;
		}
		if (sqr < 15) {
			strafingTime = 0;
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			return;
		}
		if (strafingTime >= 20) {
			if ((double) mob.getRandom().nextFloat() < 0.3D) {
				strafingClockwise = !strafingClockwise;
			}
			if ((double) mob.getRandom().nextFloat() < 0.3D) {
				strafingBackwards = !strafingBackwards;
			}
			strafingTime = 0;
		}
		if (strafingTime > -1) {
			if (dist > sqr * 0.75) {
				strafingBackwards = false;
			} else if (dist < sqr * 0.5) {
				strafingBackwards = true;
			}
			mob.getMoveControl().strafe(strafingBackwards ? -0.5F : 0.5F, strafingClockwise ? 0.5F : -0.5F);
			mob.lookAt(target, 30.0F, 30.0F);
		} else {
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}
	}

	public final double attackRadiusSqr() {
		double range = this.range(mob.getItemInHand(holder.getWeaponHand()));
		var ins = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (ins != null) {
			range = Math.min(ins.getValue(), range);
		}
		return range * range;
	}

	private boolean isHoldingGun() {
		return getGun() != null;
	}

	private boolean canReload() {
		if (holder.toUser().bypassAllConsumption()) return true;
		IGun item = this.getGun();
		if (item instanceof AbstractGunItem gun) {
			ItemStack stack = mob.getItemInHand(InteractionHand.MAIN_HAND);
			if (gun.useDummyAmmo(stack) && gun.getDummyAmmoAmount(stack) == 0) {
				stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
					tag.remove("DummyAmmo");
				}));
			}
			return gun.canReload(mob, stack);
		} else {
			return false;
		}
	}

	@Nullable
	private IGun getGun() {
		return IGun.getIGunOrNull(mob.getItemInHand(InteractionHand.MAIN_HAND));
	}

	private boolean isValidTarget() {
		return mob.getTarget() != null && mob.getTarget().isAlive();
	}


	private boolean hasAmmo() {
		return getAmmoCount(mob.getItemInHand(InteractionHand.MAIN_HAND)) != 0;
	}

	private boolean isRightAngle() {
		LivingEntity target = mob.getTarget();
		if (target != null && isValidTarget()) {
			var diff = target.getEyePosition().subtract(mob.getEyePosition());
			var deg = vectorDegreeCalculate(mob.getViewVector(1), diff);
			return deg < 10 + Math.max(0, 64 - mob.distanceToSqr(target));
		} else {
			return false;
		}
	}

	private int getAmmoCount(ItemStack stack) {
		IGun gun = IGun.getIGunOrNull(stack);
		if (gun != null) {
			GunData gunData = TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(CommonGunIndex::getGunData).orElse(null);
			if (gunData != null) {
				if (gun.useInventoryAmmo(stack)) return -1;
				return gun.getCurrentAmmoCount(stack) + (gun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT ? 1 : 0);
			}
		}

		return 0;
	}

	public double vectorDegreeCalculate(Vec3 dir, Vec3 diff) {
		double cos = dir.dot(diff) / dir.length() / diff.length();
		return Math.toDegrees(Math.acos(cos));
	}

	public boolean canMeleeAttack(IGunOperator gunOperator, LivingEntity target) {
		return System.currentTimeMillis() - gunOperator.getDataHolder().meleeTimestamp > 3000L && isValidTarget() && getAttackReachSqr(target) >= mob.distanceToSqr(target);
	}

	public double getAttackReachSqr(LivingEntity target) {
		return mob.getBbWidth() * 2 * mob.getBbWidth() * 2 + target.getBbWidth();
	}

	enum GunState {
		UNCHARGED,
		CHARGING,
		CHARGED,
		READY_TO_ATTACK;
	}

}
