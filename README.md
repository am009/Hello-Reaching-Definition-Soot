# Hello-Reching-Definition-Soot



### 使用函数内的Reaching Definition

参考`src\main\java\soot\jimple\toolkits\annotation\defs\ReachingDefsTagger.java`这里的用法。

主要用法就是两句话

```java
LocalDefs ld = G.v().soot_toolkits_scalar_LocalDefsFactory().newLocalDefs(b);
for (Unit next : ld.getDefsOfAt(l, s)) {
    Stmt stmt = (Stmt) next;
}
```

b是函数体，比如getActiveBody的返回值

l是local，从stmt里面取到的。s是stmt。这个循环一般只会执行一次。



### 自制类成员函数的交叉引用

其实是参照`src\main\java\soot\dava\toolkits\base\AST\interProcedural\ConstantFieldValueFinder.java`里写的。

遍历每个类的每个方法的每个语句，看是不是用到了成员变量，看是use还是define。这里只保存了define。

封装函数假设只有一个定义点（定义点太多会警报一下），只需要传入FieldRef就可以得到唯一的定义点，即相关的赋值语句，由于往往是其他函数，所以同时保存了SootMethod类。



## 以下的最后都没用上



尝试使用IFDSReachingDefinitions
https://github.com/Sable/heros/wiki/Example%3A-Using-Heros-with-Soot

### 环境

vscode安装Java插件后，左下角Java Projects新建一个。这次就选无build tools吧。

直接往lib里放下载好的soot jar。去https://repo1.maven.org/maven2/org/soot-oss/soot/ 下载

然后看到自己main函数上漂浮着的 `Run | Debug` 点击Run就可以了。

### 报错 solver.run NullPointerException
发现直接跑，在solver.run里面报出了NullPointerException。搜了一下，找不到为什么。

直接下载soot源码，然后IDEA打开，启动debug，然后开始调试，然后选择源码路径。

下载soot源码使用git的shallow clone: git clone git@github.com:soot-oss/soot.git --depth 1

然后在倒数第二个调用栈观察局部变量发现是在一个没有参数的函数里代码去获取了第0个参数报的错。
函数名<sun.security.action.GetPropertyAction: java.lang.String run()>

应该是最开始设置的时候要设置只加载用户代码吧，这样就不会分析到库函数了。那个初始化的时候有一个`Options.v().set_app(true);`
加上了还是报错。。。可能是`Scene.v().loadNecessaryClasses();`不太行？

最后在这里 https://github.com/soot-oss/soot/blob/2d308da32cf38a428643bbae286565b02b89547b/src/systemTest/java/soot/testing/framework/AbstractTestingFramework.java 
找到了一个`getExcludes`函数，然后加上了`Options.v().set_no_bodies_for_excluded(true);`  
`Options.v().set_exclude(getExcludes());` 两句，解决了。

还有set_include，不知道效果如何。另外设置EntryPoint不知道什么情况，要不要设置。

### 报错 no active body present for method
首先发现 `PackManager.v().runPacks();` 结束后才会有body给你遍历。

最后发现原来有两个函数，`getActiveBody` `retrieveActiveBody`,用retrieve的就行了。在wjtp的时候虽然函数体很多都没加载，但是调用retrieve的版本没有加载的会去加载。


### 有些语句找不到分析结果
在soot里搜索getEntryPoints看了下，好像IFDS的分析有入口点，可以有多个。要模仿https://github.com/Sable/heros/wiki/Example%3A-Using-Heros-with-Soot 
去添加EntryPoints。。。行吧
还有MainClass的设置，不知道有没有影响

