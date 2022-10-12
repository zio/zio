"use strict";(self.webpackChunkzio_site=self.webpackChunkzio_site||[]).push([[513],{3905:(e,t,n)=>{n.d(t,{Zo:()=>p,kt:()=>f});var r=n(67294);function a(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function i(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);t&&(r=r.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,r)}return n}function o(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?i(Object(n),!0).forEach((function(t){a(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):i(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function s(e,t){if(null==e)return{};var n,r,a=function(e,t){if(null==e)return{};var n,r,a={},i=Object.keys(e);for(r=0;r<i.length;r++)n=i[r],t.indexOf(n)>=0||(a[n]=e[n]);return a}(e,t);if(Object.getOwnPropertySymbols){var i=Object.getOwnPropertySymbols(e);for(r=0;r<i.length;r++)n=i[r],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(a[n]=e[n])}return a}var l=r.createContext({}),c=function(e){var t=r.useContext(l),n=t;return e&&(n="function"==typeof e?e(t):o(o({},t),e)),n},p=function(e){var t=c(e.components);return r.createElement(l.Provider,{value:t},e.children)},u={inlineCode:"code",wrapper:function(e){var t=e.children;return r.createElement(r.Fragment,{},t)}},m=r.forwardRef((function(e,t){var n=e.components,a=e.mdxType,i=e.originalType,l=e.parentName,p=s(e,["components","mdxType","originalType","parentName"]),m=c(n),f=a,h=m["".concat(l,".").concat(f)]||m[f]||u[f]||i;return n?r.createElement(h,o(o({ref:t},p),{},{components:n})):r.createElement(h,o({ref:t},p))}));function f(e,t){var n=arguments,a=t&&t.mdxType;if("string"==typeof e||a){var i=n.length,o=new Array(i);o[0]=m;var s={};for(var l in t)hasOwnProperty.call(t,l)&&(s[l]=t[l]);s.originalType=e,s.mdxType="string"==typeof e?e:a,o[1]=s;for(var c=2;c<i;c++)o[c]=n[c];return r.createElement.apply(null,o)}return r.createElement.apply(null,n)}m.displayName="MDXCreateElement"},45135:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>l,contentTitle:()=>o,default:()=>u,frontMatter:()=>i,metadata:()=>s,toc:()=>c});var r=n(87462),a=(n(67294),n(3905));const i={id:"subscription-ref",title:"SubscriptionRef"},o=void 0,s={unversionedId:"reference/stream/subscription-ref",id:"reference/stream/subscription-ref",title:"SubscriptionRef",description:"A SubscriptionRef[A] is a Ref that lets us subscribe to receive the current value along with all changes to that value.",source:"@site/docs/reference/stream/subscriptionref.md",sourceDirName:"reference/stream",slug:"/reference/stream/subscription-ref",permalink:"/reference/stream/subscription-ref",draft:!1,editUrl:"https://github.com/zio/zio/edit/series/2.x/docs/reference/stream/subscriptionref.md",tags:[],version:"current",frontMatter:{id:"subscription-ref",title:"SubscriptionRef"},sidebar:"reference-sidebar",previous:{title:"Channel Interruption",permalink:"/reference/stream/zchannel/channel-interruption"},next:{title:"Logging",permalink:"/reference/observability/logging"}},l={},c=[],p={toc:c};function u(e){let{components:t,...n}=e;return(0,a.kt)("wrapper",(0,r.Z)({},p,n,{components:t,mdxType:"MDXLayout"}),(0,a.kt)("p",null,"A ",(0,a.kt)("inlineCode",{parentName:"p"},"SubscriptionRef[A]")," is a ",(0,a.kt)("inlineCode",{parentName:"p"},"Ref")," that lets us subscribe to receive the current value along with all changes to that value."),(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre",className:"language-scala"},"import zio._\nimport zio.stream._\n\ntrait SubscriptionRef[A] extends Ref.Synchronized[A] {\n  def changes: ZStream[Any, Nothing, A]\n}\n")),(0,a.kt)("p",null,"We can use all the normal methods on ",(0,a.kt)("inlineCode",{parentName:"p"},"Ref.Synchronized")," to ",(0,a.kt)("inlineCode",{parentName:"p"},"get"),", ",(0,a.kt)("inlineCode",{parentName:"p"},"set"),", or ",(0,a.kt)("inlineCode",{parentName:"p"},"modify")," the current value."),(0,a.kt)("p",null,"The ",(0,a.kt)("inlineCode",{parentName:"p"},"changes")," stream can be consumed to observe the current value as well as all changes to that value. Since ",(0,a.kt)("inlineCode",{parentName:"p"},"changes")," is just a description of a stream, each time we run the stream we will observe the current value as of that point in time as well as all changes after that."),(0,a.kt)("p",null,"To create a ",(0,a.kt)("inlineCode",{parentName:"p"},"SubscriptionRef")," you can use the ",(0,a.kt)("inlineCode",{parentName:"p"},"make")," constructor, which makes a new ",(0,a.kt)("inlineCode",{parentName:"p"},"SubscriptionRef")," with the specified initial value."),(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre",className:"language-scala"},"object SubscriptionRef {\n  def make[A](a: A): UIO[SubscriptionRef[A]] =\n    ???\n}\n")),(0,a.kt)("p",null,"A ",(0,a.kt)("inlineCode",{parentName:"p"},"SubscriptionRef")," can be extremely useful to model some shared state where one or more observers must perform some action for all changes in that shared state. For example, in a functional reactive programming context the value of the ",(0,a.kt)("inlineCode",{parentName:"p"},"SubscriptionRef")," might represent one part of the application state and each observer would need to update various user interface elements based on changes in that state."),(0,a.kt)("p",null,'To see how this works, let\'s create a simple example where a "server" repeatedly updates a value that is observed by multiple "clients".'),(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre",className:"language-scala"},"def server(ref: Ref[Long]): UIO[Nothing] =\n  ref.update(_ + 1).forever\n")),(0,a.kt)("p",null,"Notice that ",(0,a.kt)("inlineCode",{parentName:"p"},"server")," just takes a ",(0,a.kt)("inlineCode",{parentName:"p"},"Ref")," and does not need to know anything about ",(0,a.kt)("inlineCode",{parentName:"p"},"SubscriptionRef"),". From its perspective it is just updating a value."),(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre",className:"language-scala"},"def client(changes: ZStream[Any, Nothing, Long]): UIO[Chunk[Long]] =\n  for {\n    n     <- Random.nextLongBetween(1, 200)\n    chunk <- changes.take(n).runCollect\n  } yield chunk\n")),(0,a.kt)("p",null,"Similarly ",(0,a.kt)("inlineCode",{parentName:"p"},"client")," just takes a ",(0,a.kt)("inlineCode",{parentName:"p"},"ZStream")," of values and does not have to know anything about the source of these values. In this case we will simply observe a fixed number of values."),(0,a.kt)("p",null,"To wire everything together, we start the server, then start multiple instances of the client in parallel, and finally shut down the server when we are done. We also actually create the ",(0,a.kt)("inlineCode",{parentName:"p"},"SubscriptionRef")," here."),(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre",className:"language-scala"},"for {\n  subscriptionRef <- SubscriptionRef.make(0L)\n  server          <- server(subscriptionRef).fork\n  chunks          <- ZIO.collectAllPar(List.fill(100)(client(subscriptionRef.changes)))\n  _               <- server.interrupt\n  _               <- ZIO.foreach(chunks)(chunk => Console.printLine(chunk))\n} yield ()\n")),(0,a.kt)("p",null,"This will ensure that each client observes the current value when it starts and all changes to the value after that."),(0,a.kt)("p",null,"Since the changes are just streams it is also easy to build much more complex programs using all the stream operators we are accustomed to. For example, we can transform these streams, filter them, or merge them with other streams."))}u.isMDXComponent=!0}}]);