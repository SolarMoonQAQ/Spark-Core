# FL模块



> 该模块采用[FL设计模式](https://gitee.com/ArcherLee_rc/archers_helicopter_forge/tree/master#%E4%B8%BB%E8%A6%81%E8%AE%BE%E8%AE%A1%E6%A8%A1%E5%BC%8F)，及 **FunctionLathe** 功能车床，目的是解耦重复使用的功能和载体，像现实中的车床一样去加工某个功能



## 寻常使用方法


$$
A代理类 \to ExampleFL功能类 \gets ProviderEvent功能提供类
$$


抽离逻辑如下：

首先定义一个FL类，这里演示两种传入代理类的方式

```java
public class ExampleFL extends FunctionLathe<A> {
    public ExampleFL() {
        super(new A()); //第一种我们使用空参生成类进行代理
    }
    public ExampleFL(A a) {
        super(a); //第二种我们定义外部传入现成类进行代理
    }
    
    public void doThings() {
        //马上实现
    }
    
}
```



- #### 当然后期也能通过setFL改变代理类，前提是你知道这不会影响性能



接下来我们可以尝试将原先的功能提供类接入该责任链

```java
public class ExampleFL extends FunctionLathe<A> {
	//...
    public void doThings() {
        A a = getFL();
        // a的一些功能代码
    }
    
}
```

这里假设mc事件每tick触发一次doThings

```java
public class ProviderEvent() {
	ExampleFunctionLathe lathe = new ExampleFunctionLathe();
	
	void tick() {
		lathe.doThings();
	}
}
```



## 进阶方法

​	假设需要代理的对象本质上不存在任何继承关系，又想实现统一的功能得以复用，那么我们需要用到接口，这里为了规范和功能类对象获取方便

你需要让这些代理类实现一个统一接口，并且一定要继承自 ***fl.Lathe*** 接口



这里用摄像头功能接口举例

```java
package io.github.sweetzonzi.machine_max.client.input;

import fl.Lathe;

public interface CameraHolder<T> extends Lathe<T> {
    // 这里可以定义自己需要的共同方法
}

```

提供类来自于公共事件

```java
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public class CameraController {
    
    @Getter
    private final static Set<CameraFunctionLathe> holders = new CollectionSet<>(new HashSet<>()); //所有的摄像头功能类
    
    @SubscribeEvent
    public static void updateCameraPos(ComputeCameraPosEvent event) {
        for (CameraFunctionLathe holder : holders) {
            holder.updateCameraPos(event); //更新功能类中的摄像机
        }
    }
}
```

定义功能基类

```java
public class CameraFunctionLathe<T extends CameraHolder<T>> extends FunctionLathe<T> { //代理对象设置为所有实现该接口的类
    
    public Camera updateCameraPos(ComputeCameraPosEvent event) {
        //具体推理逻辑
        return event.getCamera();
    }
    
    public void turnOn() { //相机开始推流
        CameraController.getHolders().add(this);
    }

    public void turnOff() { //画面停止更新
        CameraController.getHolders().remove(this);
    }
    
    //定义其他公共的功能方法...
}
```

### 根据上面的代码我们确立了摄像头事件的分发


$$
Lathe接口 \to CameraFunctionLathe功能类 \gets CameraController功能提供类
$$


### 接下来就可以实现具体方法了



全站仪方块：

```java
public class TotalStationBlock extends BaseEntityBlock implements CameraHolder<TotalStationBlock> {
    public final Camera camera = new Camera(this);
    public static class Camera extends CameraFunctionLathe<TotalStationBlock> {
        public Camera(TotalStationBlock totalStation) {
            super(totalStation);
        }
    }
    
    @Override
    public CameraFunctionLathe<TotalStationBlock> getLathe() {
        return camera;
    }
    
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        getLathe().turnOn(); //假设全站仪方块被放置时，接管摄像头
    }

}
```



座椅系统

```java
public class SeatSubsystem extends AbstractControllableSubsystem implements CameraHolder<SeatSubsystem> {
    public final Camera camera = new Camera(this);
    public static class Camera extends CameraFunctionLathe<SeatSubsystem> {
        public Camera(SeatSubsystem seat) {
            super(seat);
        }
    }
    
    @Override
    public CameraFunctionLathe<SeatSubsystem> getLathe() {
        return camera;
    }
    
     public void setPassenger(LivingEntity passenger) {
        getLathe().turnOn(); //假设开始乘坐载具时，启用载具驾驶视角
    }
}
```





演示完毕！这是一个年轻的设计模式，期待你发现更多盲区  									作者 ：[ArcherLee - 我不是大鸽纸]([我不是大鸽纸的个人空间-我不是大鸽纸个人主页-哔哩哔哩视频](https://space.bilibili.com/178065252))
