package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.entity.IEntityPatch;
import cn.solarmoon.spark_core.entity.attack.HurtDataHolder;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.skill.SkillHost;
import cn.solarmoon.spark_core.state_machine.IStateMachineHolder;
import cn.solarmoon.spark_core.sync.Syncer;

public interface EntityAll extends PhysicsHost, HurtDataHolder, SkillHost, Syncer, IPreInputHolder, IStateMachineHolder, IEntityPatch {
}
