"use strict";(self.webpackChunkzio_site=self.webpackChunkzio_site||[]).push([[7104],{3905:(e,t,r)=>{r.d(t,{Zo:()=>p,kt:()=>d});var n=r(67294);function a(e,t,r){return t in e?Object.defineProperty(e,t,{value:r,enumerable:!0,configurable:!0,writable:!0}):e[t]=r,e}function i(e,t){var r=Object.keys(e);if(Object.getOwnPropertySymbols){var n=Object.getOwnPropertySymbols(e);t&&(n=n.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),r.push.apply(r,n)}return r}function s(e){for(var t=1;t<arguments.length;t++){var r=null!=arguments[t]?arguments[t]:{};t%2?i(Object(r),!0).forEach((function(t){a(e,t,r[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(r)):i(Object(r)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(r,t))}))}return e}function o(e,t){if(null==e)return{};var r,n,a=function(e,t){if(null==e)return{};var r,n,a={},i=Object.keys(e);for(n=0;n<i.length;n++)r=i[n],t.indexOf(r)>=0||(a[r]=e[r]);return a}(e,t);if(Object.getOwnPropertySymbols){var i=Object.getOwnPropertySymbols(e);for(n=0;n<i.length;n++)r=i[n],t.indexOf(r)>=0||Object.prototype.propertyIsEnumerable.call(e,r)&&(a[r]=e[r])}return a}var l=n.createContext({}),m=function(e){var t=n.useContext(l),r=t;return e&&(r="function"==typeof e?e(t):s(s({},t),e)),r},p=function(e){var t=m(e.components);return n.createElement(l.Provider,{value:t},e.children)},u={inlineCode:"code",wrapper:function(e){var t=e.children;return n.createElement(n.Fragment,{},t)}},c=n.forwardRef((function(e,t){var r=e.components,a=e.mdxType,i=e.originalType,l=e.parentName,p=o(e,["components","mdxType","originalType","parentName"]),c=m(r),d=a,f=c["".concat(l,".").concat(d)]||c[d]||u[d]||i;return r?n.createElement(f,s(s({ref:t},p),{},{components:r})):n.createElement(f,s({ref:t},p))}));function d(e,t){var r=arguments,a=t&&t.mdxType;if("string"==typeof e||a){var i=r.length,s=new Array(i);s[0]=c;var o={};for(var l in t)hasOwnProperty.call(t,l)&&(o[l]=t[l]);o.originalType=e,o.mdxType="string"==typeof e?e:a,s[1]=o;for(var m=2;m<i;m++)s[m]=r[m];return n.createElement.apply(null,s)}return n.createElement.apply(null,r)}c.displayName="MDXCreateElement"},19761:(e,t,r)=>{r.r(t),r.d(t,{assets:()=>l,contentTitle:()=>s,default:()=>u,frontMatter:()=>i,metadata:()=>o,toc:()=>m});var n=r(87462),a=(r(67294),r(3905));const i={id:"summary",title:"Summary"},s=void 0,o={unversionedId:"reference/observability/metrics/summary",id:"reference/observability/metrics/summary",title:"Summary",description:"A Summary represents a sliding window of a time series along with metrics for certain percentiles of the time series, referred to as quantiles.",source:"@site/docs/reference/observability/metrics/summary.md",sourceDirName:"reference/observability/metrics",slug:"/reference/observability/metrics/summary",permalink:"/reference/observability/metrics/summary",draft:!1,editUrl:"https://github.com/zio/zio/edit/series/2.x/docs/reference/observability/metrics/summary.md",tags:[],version:"current",frontMatter:{id:"summary",title:"Summary"},sidebar:"reference-sidebar",previous:{title:"Histogram",permalink:"/reference/observability/metrics/histogram"},next:{title:"Frequency",permalink:"/reference/observability/metrics/setcount"}},l={},m=[{value:"Internals",id:"internals",level:2},{value:"API",id:"api",level:2},{value:"Use Cases",id:"use-cases",level:2},{value:"Examples",id:"examples",level:2}],p={toc:m};function u(e){let{components:t,...r}=e;return(0,a.kt)("wrapper",(0,n.Z)({},p,r,{components:t,mdxType:"MDXLayout"}),(0,a.kt)("p",null,"A ",(0,a.kt)("inlineCode",{parentName:"p"},"Summary")," represents a sliding window of a time series along with metrics for certain percentiles of the time series, referred to as quantiles."),(0,a.kt)("p",null,"Quantiles describe specified percentiles of the sliding window that are of interest. For example, if we were using a summary to track the response time for requests over the last hour then we might be interested in the 50th percentile, 90th percentile, 95th percentile, and 99th percentile for response times."),(0,a.kt)("h2",{id:"internals"},"Internals"),(0,a.kt)("p",null,"Similar to a ",(0,a.kt)("a",{parentName:"p",href:"/reference/observability/metrics/histogram"},"histogram")," a summary also observes ",(0,a.kt)("em",{parentName:"p"},"Double")," values. While a histogram directly modifies the bucket counters and does not keep the individual samples, the summary keeps the observed samples in its internal state. To avoid the set of samples grow uncontrolled, the summary needs to be configured with a maximum age ",(0,a.kt)("inlineCode",{parentName:"p"},"t")," and a maximum size ",(0,a.kt)("inlineCode",{parentName:"p"},"n"),". To calculate the statistics, maximal ",(0,a.kt)("inlineCode",{parentName:"p"},"n")," samples will be used, all of which are not older than ",(0,a.kt)("inlineCode",{parentName:"p"},"t"),"."),(0,a.kt)("p",null,"Essentially, the set of samples is a ",(0,a.kt)("em",{parentName:"p"},"sliding window")," over the last observed samples matching the conditions above."),(0,a.kt)("p",null,"A summary is used to calculate a set of quantiles over the current set of samples. A quantile is defined by a ",(0,a.kt)("em",{parentName:"p"},"Double")," value ",(0,a.kt)("inlineCode",{parentName:"p"},"q")," with ",(0,a.kt)("inlineCode",{parentName:"p"},"0 <= q <= 1")," and resolves to a ",(0,a.kt)("inlineCode",{parentName:"p"},"Double")," as well."),(0,a.kt)("p",null,"The value of a given quantile ",(0,a.kt)("inlineCode",{parentName:"p"},"q")," is the maximum value ",(0,a.kt)("inlineCode",{parentName:"p"},"v")," out of the current sample buffer with size ",(0,a.kt)("inlineCode",{parentName:"p"},"n")," where at most ",(0,a.kt)("inlineCode",{parentName:"p"},"q * n")," values out of the sample buffer are less or equal to ",(0,a.kt)("inlineCode",{parentName:"p"},"v"),"."),(0,a.kt)("p",null,"Typical quantiles for observation are ",(0,a.kt)("inlineCode",{parentName:"p"},"0.5")," (the median) and the ",(0,a.kt)("inlineCode",{parentName:"p"},"0.95"),". Quantiles are very good for monitoring ",(0,a.kt)("em",{parentName:"p"},"Service Level Agreements"),"."),(0,a.kt)("p",null,"The ZIO Metrics API also allows summaries to be configured with an error margin ",(0,a.kt)("inlineCode",{parentName:"p"},"e"),". The error margin is applied to the count of values, so that a quantile ",(0,a.kt)("inlineCode",{parentName:"p"},"q")," for a set of size ",(0,a.kt)("inlineCode",{parentName:"p"},"s")," resolves to value ",(0,a.kt)("inlineCode",{parentName:"p"},"v")," if the number ",(0,a.kt)("inlineCode",{parentName:"p"},"n")," of values less or equal to ",(0,a.kt)("inlineCode",{parentName:"p"},"v")," is ",(0,a.kt)("inlineCode",{parentName:"p"},"(1 -e)q * s <= n <= (1+e)q"),"."),(0,a.kt)("h2",{id:"api"},"API"),(0,a.kt)("p",null,"TODO"),(0,a.kt)("h2",{id:"use-cases"},"Use Cases"),(0,a.kt)("p",null,"Like ",(0,a.kt)("a",{parentName:"p",href:"/reference/observability/metrics/histogram"},"histograms"),", summaries are used for ",(0,a.kt)("em",{parentName:"p"},"monitoring latencies"),", but they don't require us to define buckets. So, summaries are the best choice in these situations:"),(0,a.kt)("ul",null,(0,a.kt)("li",{parentName:"ul"},"When histograms are not proper for us, in terms of accuracy"),(0,a.kt)("li",{parentName:"ul"},"When we can't use histograms, as we don't have a good estimation of the range of values"),(0,a.kt)("li",{parentName:"ul"},"When we don't need to perform aggregation or averages across multiple instances, as the calculations are done on the application side")),(0,a.kt)("h2",{id:"examples"},"Examples"),(0,a.kt)("p",null,"Create a summary that can hold ",(0,a.kt)("inlineCode",{parentName:"p"},"100")," samples, the max age of the samples is ",(0,a.kt)("inlineCode",{parentName:"p"},"1 day")," and the error margin is ",(0,a.kt)("inlineCode",{parentName:"p"},"3%"),". The summary should report the ",(0,a.kt)("inlineCode",{parentName:"p"},"10%"),", ",(0,a.kt)("inlineCode",{parentName:"p"},"50%")," and ",(0,a.kt)("inlineCode",{parentName:"p"},"90%")," Quantile. It can be applied to effects yielding an ",(0,a.kt)("inlineCode",{parentName:"p"},"Int"),":"),(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre",className:"language-scala"},"TODO\n")),(0,a.kt)("p",null,"Now we can apply this aspect to an effect producing an ",(0,a.kt)("inlineCode",{parentName:"p"},"Int"),":"),(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre",className:"language-scala"},"TODO\n")))}u.isMDXComponent=!0}}]);