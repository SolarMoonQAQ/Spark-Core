Skill.create("spark_core:sword_combo_0", builder => {
    builder.setPriority(100)
    builder.accept(skill => {
        const entity = skill.getHolder().asEntity()
        const animatable = skill.getHolder().asAnimatable()

        if (entity == null || animatable == null) return

        const anim = animatable.createAnimation('sword:combo_0')
        const attackBody = PhysicsHelper.createCollisionBoxBoundToBone(animatable, 'rightItem', SpMath.vec3(1.0, 1.0, 2.0), SpMath.vec3(0.0, 0.0, -1.0))

        attackBody.onAttackCollide('attack', {
            doAttack: (attacker, target, o1, o2, manifoldId) => {
                EntityHelper.commonAttack(entity, target)
            }
        })

        anim.onEnd(event => {
            skill.end()
        })

        skill.onActiveStart(() => {
            animatable.playAnimation(anim, 0)
        })

        skill.onActive(() => {
            const animTime = anim.getTime()
            if (animTime >= 0.2 && animTime <= 0.3) {
                EntityHelper.move(entity, SpMath.vec3(0.0, entity.getDeltaMovement().y, 0.5), false)
            }

            if (animTime >= 0.25 && animTime <= 0.4) {
                attackBody.setCollideWithGroups(1)
            } else {
                attackBody.setCollideWithGroups(0)
            }
        })

        skill.onLocalInputUpdate(event => {
            EntityHelper.preventLocalInput(event)
        })

        skill.onEnd(() => {
            attackBody.remove()
        })
    })
})