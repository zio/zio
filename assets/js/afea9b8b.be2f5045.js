"use strict";(self.webpackChunkzio_site=self.webpackChunkzio_site||[]).push([[1302],{3905:(e,t,n)=>{n.d(t,{Zo:()=>c,kt:()=>u});var i=n(67294);function r(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function a(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var i=Object.getOwnPropertySymbols(e);t&&(i=i.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,i)}return n}function o(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?a(Object(n),!0).forEach((function(t){r(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):a(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function p(e,t){if(null==e)return{};var n,i,r=function(e,t){if(null==e)return{};var n,i,r={},a=Object.keys(e);for(i=0;i<a.length;i++)n=a[i],t.indexOf(n)>=0||(r[n]=e[n]);return r}(e,t);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(i=0;i<a.length;i++)n=a[i],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(r[n]=e[n])}return r}var l=i.createContext({}),s=function(e){var t=i.useContext(l),n=t;return e&&(n="function"==typeof e?e(t):o(o({},t),e)),n},c=function(e){var t=s(e.components);return i.createElement(l.Provider,{value:t},e.children)},d={inlineCode:"code",wrapper:function(e){var t=e.children;return i.createElement(i.Fragment,{},t)}},m=i.forwardRef((function(e,t){var n=e.components,r=e.mdxType,a=e.originalType,l=e.parentName,c=p(e,["components","mdxType","originalType","parentName"]),m=s(n),u=r,h=m["".concat(l,".").concat(u)]||m[u]||d[u]||a;return n?i.createElement(h,o(o({ref:t},c),{},{components:n})):i.createElement(h,o({ref:t},c))}));function u(e,t){var n=arguments,r=t&&t.mdxType;if("string"==typeof e||r){var a=n.length,o=new Array(a);o[0]=m;var p={};for(var l in t)hasOwnProperty.call(t,l)&&(p[l]=t[l]);p.originalType=e,p.mdxType="string"==typeof e?e:r,o[1]=p;for(var s=2;s<a;s++)o[s]=n[s];return i.createElement.apply(null,o)}return i.createElement.apply(null,n)}m.displayName="MDXCreateElement"},97433:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>l,contentTitle:()=>o,default:()=>d,frontMatter:()=>a,metadata:()=>p,toc:()=>s});var i=n(87462),r=(n(67294),n(3905));const a={id:"index",title:"Introduction to Dependency Injection in ZIO"},o=void 0,p={unversionedId:"reference/di/index",id:"reference/di/index",title:"Introduction to Dependency Injection in ZIO",description:"What is Dependency?",source:"@site/docs/reference/di/index.md",sourceDirName:"reference/di",slug:"/reference/di/",permalink:"/reference/di/",draft:!1,editUrl:"https://github.com/zio/zio/edit/series/2.x/docs/reference/di/index.md",tags:[],version:"current",frontMatter:{id:"index",title:"Introduction to Dependency Injection in ZIO"},sidebar:"reference-sidebar",previous:{title:"Three Laws of ZIO Environment",permalink:"/reference/architecture/the-three-laws-of-zio-environment"},next:{title:"Motivation",permalink:"/reference/di/motivation"}},l={},s=[{value:"What is Dependency?",id:"what-is-dependency",level:2},{value:"What is Dependency Injection?",id:"what-is-dependency-injection",level:2},{value:"ZIO&#39;s Built-in Dependency Injection",id:"zios-built-in-dependency-injection",level:2},{value:"ZIO&#39;s Dependency Injection Features",id:"zios-dependency-injection-features",level:2},{value:"Other Frameworks",id:"other-frameworks",level:2}],c={toc:s};function d(e){let{components:t,...n}=e;return(0,r.kt)("wrapper",(0,i.Z)({},c,n,{components:t,mdxType:"MDXLayout"}),(0,r.kt)("h2",{id:"what-is-dependency"},"What is Dependency?"),(0,r.kt)("p",null,"When we implement a service, we might need to use other services. So a dependency is just another service that is required to fulfill its functionality:"),(0,r.kt)("pre",null,(0,r.kt)("code",{parentName:"pre",className:"language-scala"},"class Editor {\n val formatter = new Formatter\n val compiler = new Compiler\n \n def formatAndCompile(code: String): UIO[String] = \n formatter.format(code).flatMap(compiler.compile)\n}\n")),(0,r.kt)("h2",{id:"what-is-dependency-injection"},"What is Dependency Injection?"),(0,r.kt)("p",null,"Dependency injection is a pattern for decoupling the usage of dependencies from their actual creation process. In other words, it is a process of injecting dependencies of service from the outside world. The service itself doesn't know how to create its dependencies."),(0,r.kt)("p",null,"The following example shows an ",(0,r.kt)("inlineCode",{parentName:"p"},"Editor")," service that depends on ",(0,r.kt)("inlineCode",{parentName:"p"},"Formatter")," and ",(0,r.kt)("inlineCode",{parentName:"p"},"Compiler")," services. It doesn't use dependency injection:"),(0,r.kt)("pre",null,(0,r.kt)("code",{parentName:"pre",className:"language-scala"},"import zio._  \n\nclass Editor {\n private val formatter = new Formatter\n private val compiler = new Compiler\n \n def formatAndCompile(code: String): UIO[String] =\n formatter.format(code).flatMap(compiler.compile)\n}\n")),(0,r.kt)("p",null,"The ",(0,r.kt)("inlineCode",{parentName:"p"},"Editor")," class in the above example is responsible for creating the ",(0,r.kt)("inlineCode",{parentName:"p"},"Formatter")," and ",(0,r.kt)("inlineCode",{parentName:"p"},"Compiler")," services. The client of the ",(0,r.kt)("inlineCode",{parentName:"p"},"Editor")," class doesn't have any control over these services. The client can't use a different implementation for the ",(0,r.kt)("inlineCode",{parentName:"p"},"Formatter")," and ",(0,r.kt)("inlineCode",{parentName:"p"},"Compiler")," services. So it makes it hard to test the ",(0,r.kt)("inlineCode",{parentName:"p"},"Editor")," class."),(0,r.kt)("p",null,"Let's try to change the above example to use the constructor-based dependency injection pattern:"),(0,r.kt)("pre",null,(0,r.kt)("code",{parentName:"pre",className:"language-scala"},"import zio._\n\nclass Editor(formatter: Formatter, compiler: Compiler) {\n def formatAndCompile(code: String): UIO[String] = ???\n}\n")),(0,r.kt)("p",null,"In this example, the ",(0,r.kt)("inlineCode",{parentName:"p"},"Editor")," service is not responsible for creating its dependencies. Instead, they are expected to be injected from the caller site. The ",(0,r.kt)("inlineCode",{parentName:"p"},"Editor")," service does not know how its dependencies are created, they are just injected into its constructor."),(0,r.kt)("p",null,"So dependency injection is a very simple concept and can be implemented with simple constructs. In a lot of situations, we are not required to use any tools or frameworks."),(0,r.kt)("p",null,"In the ",(0,r.kt)("a",{parentName:"p",href:"/reference/di/motivation"},"motivation page")," we explain why applications should use the dependency injection pattern in more detail."),(0,r.kt)("h2",{id:"zios-built-in-dependency-injection"},"ZIO's Built-in Dependency Injection"),(0,r.kt)("p",null,"ZIO has a full solution to the dependency injection problem. It provides a built-in approach to dependency injection using the following tools in combination together:"),(0,r.kt)("ol",null,(0,r.kt)("li",{parentName:"ol"},(0,r.kt)("p",{parentName:"li"},(0,r.kt)("strong",{parentName:"p"},"ZIO Environment")),(0,r.kt)("ol",{parentName:"li"},(0,r.kt)("li",{parentName:"ol"},(0,r.kt)("p",{parentName:"li"},"We use the ",(0,r.kt)("inlineCode",{parentName:"p"},"ZIO.serviceXYZ")," to access services inside the ZIO environment, without having any knowledge of how the services are created or implemented. Using ",(0,r.kt)("inlineCode",{parentName:"p"},"ZIO.serviceXYZ")," helps us to decouple our usage of services from the implementation of the services."),(0,r.kt)("p",{parentName:"li"},"Consequently, all dependencies will be encoded inside the ",(0,r.kt)("inlineCode",{parentName:"p"},"R")," type parameter of our ZIO application. This specifies which services are required to fulfill the application's functionality.")),(0,r.kt)("li",{parentName:"ol"},(0,r.kt)("p",{parentName:"li"},"We use the ",(0,r.kt)("inlineCode",{parentName:"p"},"ZIO.provideXYZ")," to provide services to the ZIO environment. This is the opposite operation of ",(0,r.kt)("inlineCode",{parentName:"p"},"ZIO.serviceXYZ"),". It allows us to inject all dependencies into the ZIO environment.")))),(0,r.kt)("li",{parentName:"ol"},(0,r.kt)("p",{parentName:"li"},(0,r.kt)("strong",{parentName:"p"},"ZLayer"),"\u2014 We use layers to create the dependency graph that our application depends on."))),(0,r.kt)("p",null,"We can have dependency injection through three simple steps:"),(0,r.kt)("ol",null,(0,r.kt)("li",{parentName:"ol"},"Accessing services from the ZIO environment"),(0,r.kt)("li",{parentName:"ol"},"Building the dependency graph"),(0,r.kt)("li",{parentName:"ol"},"Providing services to the ZIO environment")),(0,r.kt)("p",null,"We will discuss them in more detail throughout ",(0,r.kt)("a",{parentName:"p",href:"/reference/di/dependency-injection-in-zio"},"this page"),"."),(0,r.kt)("h2",{id:"zios-dependency-injection-features"},"ZIO's Dependency Injection Features"),(0,r.kt)("p",null,"Dependency injection in ZIO is very powerful which increases the developer productivity. Let's recap some important features of dependency injection in ZIO:"),(0,r.kt)("ol",null,(0,r.kt)("li",{parentName:"ol"},(0,r.kt)("p",{parentName:"li"},(0,r.kt)("strong",{parentName:"p"},"Composable")),(0,r.kt)("ol",{parentName:"li"},(0,r.kt)("li",{parentName:"ol"},(0,r.kt)("p",{parentName:"li"},(0,r.kt)("strong",{parentName:"p"},"Composable Environment"),"\u2014 Because of the very composable nature of the ",(0,r.kt)("inlineCode",{parentName:"p"},"ZIO")," data type, its environment type parameter is also composable. So when we compose multiple ",(0,r.kt)("inlineCode",{parentName:"p"},"ZIO")," effects, where each one requires a specific service, we finally get a ",(0,r.kt)("inlineCode",{parentName:"p"},"ZIO")," effect that requires all the required services that each of the composed effects requires. "),(0,r.kt)("p",{parentName:"li"},"For example, if we ",(0,r.kt)("inlineCode",{parentName:"p"},"zip")," two effects of type ",(0,r.kt)("inlineCode",{parentName:"p"},"ZIO[A, Nothing, Int]")," and ",(0,r.kt)("inlineCode",{parentName:"p"},"ZIO[B, Throwable, String]"),", the result of this operation will become ",(0,r.kt)("inlineCode",{parentName:"p"},"ZIO[A with B, Throwable, (Int, String)]"),". The result operation requires both ",(0,r.kt)("inlineCode",{parentName:"p"},"A")," and ",(0,r.kt)("inlineCode",{parentName:"p"},"B")," services.")),(0,r.kt)("li",{parentName:"ol"},(0,r.kt)("p",{parentName:"li"},(0,r.kt)("strong",{parentName:"p"},"Composable Dependencies"),"\u2014 The ",(0,r.kt)("inlineCode",{parentName:"p"},"ZLayer")," is also composable, As well as the ZIO's environment type parameter. So we can compose multiple layers to ",(0,r.kt)("a",{parentName:"p",href:"/reference/di/building-dependency-graph"},"create a complex dependency graph"),".")))),(0,r.kt)("li",{parentName:"ol"},(0,r.kt)("p",{parentName:"li"},(0,r.kt)("strong",{parentName:"p"},"Type-Safe"),"\u2014 All the required dependencies should be provided at compile time. If we forget to provide the required services at compile time, we will get a compile error. So if our program compiles successfully, we are sure that we haven't runtime errors due to missing dependencies.")),(0,r.kt)("li",{parentName:"ol"},(0,r.kt)("p",{parentName:"li"},(0,r.kt)("strong",{parentName:"p"},"Effectful"),"\u2014 We build dependency graphs using ",(0,r.kt)("inlineCode",{parentName:"p"},"ZLayer"),". Since ",(0,r.kt)("inlineCode",{parentName:"p"},"ZLayer")," is effectful, we can create a dependency graph in an effectful way.")),(0,r.kt)("li",{parentName:"ol"},(0,r.kt)("p",{parentName:"li"},(0,r.kt)("strong",{parentName:"p"},"Resourceful"),"\u2014 It also helps us to have resourceful dependencies, where we can manage the creation and release phases of the dependencies.")),(0,r.kt)("li",{parentName:"ol"},(0,r.kt)("p",{parentName:"li"},(0,r.kt)("strong",{parentName:"p"},"Parallelism"),"\u2014 All dependencies are created in parallel, and will be provided to our application."))),(0,r.kt)("h2",{id:"other-frameworks"},"Other Frameworks"),(0,r.kt)("p",null,"Using ",(0,r.kt)("inlineCode",{parentName:"p"},"ZLayer")," along with the ZIO environment to use dependency injection is optional. While we encourage users to use ZIO's idiomatic dependency injection, it is not mandatory."),(0,r.kt)("p",null,"We can still use other DI solutions. Here are some other options:"),(0,r.kt)("ul",null,(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("a",{parentName:"li",href:"https://github.com/google/guice"},"Guice")),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("a",{parentName:"li",href:"https://izumi.7mind.io/distage/index.html"},"izumi distage")),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("a",{parentName:"li",href:"https://github.com/softwaremill/macwire"},"MacWire"))))}d.isMDXComponent=!0}}]);