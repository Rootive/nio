# Network by Rootive @已过时
## 关于 RPC
### 1、快速了解
rpc模块不对本项目的nio模块有依赖，它通过Transmission接口与其他各种网络层合作。nio_rpc模块完成了两个模块的整合。

在nio_rpc模块的test文件夹下提供了一个示例。序列化使用的是Json，因此支持自定义类的传输（暂时采用的是Jackson库，但我不是很喜欢它，可能会换）。由于rpc模块下的Invoker类使用了暴力反射，所以运行客户端的时候需要添加VM Option：“--add-opens java.base/java.lang.reflect=ALL-UNNAMED”。

您可以先不急着去运行这个示例，接下来我会突出介绍本rpc的用法与特点。
#### 1) 该示例使用的类
很简单的Color类，属性为RGB的3个值。还有一个Dog类，它实现了DogInterface。DogInterface供动态代理使用，声明了我们想要rpc的方法。

实际上，本rpc模块提供了一种不需要动态代理的调用方式，它的功能更多、并且不需要额外的DogInterface，我们将在最后介绍。

    class Color {
        int r;
        int g;
        int b;
        public Color(int r, int g, int b) {...}
        /* 此处省略默认构造方法、Getters、Setters */
    }

    public class Dog implements DogInterface {
        String name;
        int age;
        Color color;
        public Dog(String name, int age, Color color) {...}

        //为了便于动态代理的使用
        public Dog info() {
            return this;
        }

        //克隆一只狗，但给他起个新名字
        public Dog fork(String newName) {
            return new Dog(newName, age, color);
        }

        //判断两只狗年龄是否一样
        public boolean isTheSameAgeWith(Dog another) {
            return age == another.age;
        }

        //判断两只狗年龄是否一样。主要用于配合动态代理的使用
        public boolean isTheSameAgeWith(DogInterface another) {
            return age == another.info().getAge();
        }

        /* 此处省略默认构造方法、Getters、Setters */
    }
    
    /* 此接口供动态代理使用，声明了我们想要rpc的方法 */
    interface DogInterface {
        Dog info();
        Dog fork(String newName);
        boolean isTheSameAgeWith(DogInterface another);
        boolean isTheSameAgeWith(Dog another);
    }
    
#### 2) Server部分

即ServerTest类：

    public class ServerTest {
        public static void main(String[] args) throws Exception {
            //获取ServerStub
            Server server = new Server();
            var stub = server.getStub(); 
    
            //这三行代码将Dog类注册到ServerStub
            var namespace = new Namespace(Dog.class);
            namespace.autoRegisterFunctions(Dog.class);
            stub.register(namespace); 
    
            //Signature（签名）是每一个方法、对象的唯一标志。他们的标识符分别是aDog、bDog
            Signature aDogSignature = new Signature(Dog.class, "aDog");
            Signature bDogSignature = new Signature(Dog.class, "bDog");
    
            //将两个Dog对象注册到ServerStub中，ClientStub将可以通过签名找到对应的对象
            stub.register(aDogSignature, new Dog("aDog", 11, new Color(10, 101, 110)));
            stub.register(bDogSignature, new Dog("bDog", 12, new Color(20, 202, 220)));
    
            //启动服务器
            server.init(new InetSocketAddress(45555));
            server.start();
        }
    }
我们注册了Dog类，同时还注册了他的两个实例。

#### 3) Client部分

即ClientTest类，该部分有不少连接服务器的代码，这不是我们介绍的重点，因此暂时屏蔽。直接来看我们在Client端做了什么。

    //获取ClientStub。client是连接服务器相关的对象。
    var stub = client.getStub();

    //这三行代码完成了对aDog的远程引用
    Signature aDogSignature = new Signature(Dog.class, "aDog"); //与我们在ServerStub里做的一样
    Reference aDogReference = stub.sig(aDogSignature); //Reference类代表着对服务器上某个对象的引用
    DogInterface aDogIf =   //通过动态代理创建了一个代理对象，我们对其调用的方法都将在Server上对aDog执行
        (DogInterface) stub.proxyOfInterface(Dog.class, aDogReference); 

    //与上一段差不多，但这次是对bDog的远程引用
    Reference bDogReference = stub.sig(Dog.class, "bDog");
    DogInterface bDogIf = (DogInterface) stub.proxyOfInterface(Dog.class, bDogReference);

    boolean b;
    
    //从服务器上获取了aDog的信息 
    //aDog的值：{"name":"aDog","age":11,"color":{"r":10,"g":101,"b":110}}
    Dog aDog = aDogIf.info(); 
    
    //将本地的存储的aDog信息传给服务器，然后服务器对bDog调用isTheSameAgeWith
    //此处b的值：false
    b = bDogIf.isTheSameAgeWith(aDog); 
    
    //这可能会让您困扰：bDogIf是一个实现了DogInterface的Proxy类，我们将他作为参数传给rpc？
    //本rpc模块会将Proxy类当作引用，这里会通过暴力反射获取到该代理对象关联的Reference类
    //此处b的值：false
    b = aDogIf.isTheSameAgeWith(bDogIf); 

    //嵌套的2次rpc调用，调用fork将cDog存储在本地，再将cDog传给服务器调用isTheSameAgeWith
    //看起来可以合并成1次rpc，减少一次cDog的传输，这是我们下一个部分要介绍的。
    b = aDogIf.isTheSameAgeWith(bDogIf.fork("cDog"));

#### 4) 不使用动态代理的调用方式

我们前面提到，这种不使用动态代理的调用方式不需要额外的DogInterface。因此，我们可以把与DogInterface相关的东西完全忘掉。

    //获取两个method。因为我们不再使用动态代理了，这种事情需要我们自己做了。
    Method isTheSameAgeWith = Dog.class.getMethod("isTheSameAgeWith", Dog.class);
    Method fork = Dog.class.getMethod("fork", String.class);
    
    /**标准的调用方式是：ClientStub.signatureIs(...).argumentsAre(...).invoke().waitForReturn(...)
     * 为Method创建一个Signature的过程有点长，因此这种调用方式简化成：
     * ClientStub.method(...).arg(...).invoke().ret(...)
     * 通过方法名就可以轻松的猜出他的用法：
     * 1、将要调用的Method对象传入method(...)；
     * 2、对象和参数传入arg()；这里会将Proxy类、Reference类识别为引用。
     * 3、接着invoke()负责发送给Server；
     * 4、将返回值的类型传入ret(...)——Json反序列化需要对应的类型，ret(...)会阻塞，直到Server回应
     */
    b = (boolean) stub.method(isTheSameAgeWith).arg(
            aDogReference,
            bDogReference
    ).invoke().ret(boolean.class);

    /**嵌套的2次rpc调用。
     * 动态代理的方式也可以嵌套的调用rpc，但正如我们在上一部分所说的，它传输了冗余的cDog数据。
     * 而这种方式将这2次调用合成一次会话——没有冗余的cDog数据传输。
     * arg()方法返回的类型是Invoker类。arg()方法会对Invoker类特殊处理。
     * 您可以任意嵌套。只有invoke()才会真正的向服务器发送信息。我们可以认为调用了几次invoke()就发送了几次数据。
     */
    b = (boolean) stub.method(isTheSameAgeWith).arg(
            aDogReference,
            stub.method(fork).arg(bDogReference, "cDog")
    ).invoke().ret(boolean.class);
实际上，动态代理的方式本质上就是使用了这种方式。他们都会对Proxy类、Reference类、Invoker类特殊处理。
# 总结
由于我对rpc具体的业务场景不熟悉，不清楚它需要哪些特性。所以我在上述介绍的引用参数、嵌套调用可能在实际中并无必要。
