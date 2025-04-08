Skill.create("spark_core:test", builder => {
    builder.setPriority(100)
    builder.accept(skill => {
        const entity = skill.getHolder().asEntity()
        const animatable = skill.getHolder().asAnimatable()

        if (entity == null || animatable == null) return

        const originModel = animatable.getModelIndex()
        const anim = animatable.createAnimation("minecraft:player", 'attack')
        const attackBody = SpPhysicsHelper.createCollisionBoxBoundToBone(animatable, 'bone', SpMath.vec3(1.0, 1.0, 2.0), SpMath.vec3(0.0, 0.0, -1.0))

        attackBody.onAttackCollide('attack', {
            doAttack: (attacker, target, o1, o2, manifoldId) => {
                SpEntityHelper.commonAttack(entity, target)
            }
        }, null)

        anim.onEnd(event => {
            skill.end()
        })
        
        skill.onActiveStart(() => {
            animatable.setModelIndex(SpAnimHelper.createModelIndex("minecraft:hand", "minecraft:test"))
            animatable.playAnimation(anim, 0)
        })

        skill.onActive(() => {
            const animTime = anim.getTime()
            if (animTime >= 0.2 && animTime <= 0.3) {
                SpEntityHelper.move(entity, SpMath.vec3(0.0, entity.getDeltaMovement().y, 0.5), false)
            }

            if (animTime >= 0.25 && animTime <= 0.4) {
                attackBody.setCollideWithGroups(1)
            } else {
                attackBody.setCollideWithGroups(0)
            }
        })

        skill.onLocalInputUpdate(event => {
            SpEntityHelper.preventLocalInput(event)
        })

        skill.onEnd(() => {
            animatable.setModelIndex(originModel)
            attackBody.remove()
        })
    })
})