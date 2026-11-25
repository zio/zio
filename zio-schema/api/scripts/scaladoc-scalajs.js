let dropdownHandler,filterFunction;
(function(){
'use strict';
var $p;
var $fileLevelThis = this;
var $getOwnPropertyDescriptors = (Object.getOwnPropertyDescriptors || (() => {
  var ownKeysFun;
  if ((((typeof Reflect) !== "undefined") && Reflect.ownKeys)) {
    ownKeysFun = Reflect.ownKeys;
  } else {
    var getOwnPropertySymbols = (Object.getOwnPropertySymbols || ((o) => []));
    ownKeysFun = ((o) => Object.getOwnPropertyNames(o).concat(getOwnPropertySymbols(o)));
  }
  return ((o) => {
    var ownKeys = ownKeysFun(o);
    var descriptors = ({});
    var len = (ownKeys.length | 0);
    var i = 0;
    while ((i !== len)) {
      var key = ownKeys[i];
      Object.defineProperty(descriptors, key, ({
        "configurable": true,
        "enumerable": true,
        "writable": true,
        "value": Object.getOwnPropertyDescriptor(o, key)
      }));
      i = ((i + 1) | 0);
    }
    return descriptors;
  });
})());
function $Char(c) {
  this.c = c;
}
$p = $Char.prototype;
$p.toString = (function() {
  return String.fromCharCode(this.c);
});
function $Long(lo, hi) {
  this.l = lo;
  this.h = hi;
}
$p = $Long.prototype;
$p.toString = (function() {
  return $s_RTLong__toString__I__I__T(this.l, this.h);
});
function $noIsInstance(arg0) {
  throw new TypeError("Cannot call isInstance() on a Class representing a JS trait/object");
}
function $objectClone(arg0) {
  return Object.create(Object.getPrototypeOf(arg0), $getOwnPropertyDescriptors(arg0));
}
function $objectOrArrayClone(arg0) {
  return (arg0.$classData.Z ? arg0.h() : $objectClone(arg0));
}
function $objectGetClass(arg0) {
  switch ((typeof arg0)) {
    case "string": {
      return $d_T.l();
    }
    case "number": {
      if ($isInt(arg0)) {
        if ((((arg0 << 24) >> 24) === arg0)) {
          return $d_jl_Byte.l();
        } else if ((((arg0 << 16) >> 16) === arg0)) {
          return $d_jl_Short.l();
        } else {
          return $d_jl_Integer.l();
        }
      } else if ($isFloat(arg0)) {
        return $d_jl_Float.l();
      } else {
        return $d_jl_Double.l();
      }
    }
    case "boolean": {
      return $d_jl_Boolean.l();
    }
    case "undefined": {
      return $d_jl_Void.l();
    }
    default: {
      if ((arg0 instanceof $Long)) {
        return $d_jl_Long.l();
      } else if ((arg0 instanceof $Char)) {
        return $d_jl_Character.l();
      } else if ((!(!(arg0 && arg0.$classData)))) {
        return arg0.$classData.l();
      } else {
        return null;
      }
    }
  }
}
function $objectClassName(arg0) {
  switch ((typeof arg0)) {
    case "string": {
      return "java.lang.String";
    }
    case "number": {
      if ($isInt(arg0)) {
        if ((((arg0 << 24) >> 24) === arg0)) {
          return "java.lang.Byte";
        } else if ((((arg0 << 16) >> 16) === arg0)) {
          return "java.lang.Short";
        } else {
          return "java.lang.Integer";
        }
      } else if ($isFloat(arg0)) {
        return "java.lang.Float";
      } else {
        return "java.lang.Double";
      }
    }
    case "boolean": {
      return "java.lang.Boolean";
    }
    case "undefined": {
      return "java.lang.Void";
    }
    default: {
      if ((arg0 instanceof $Long)) {
        return "java.lang.Long";
      } else if ((arg0 instanceof $Char)) {
        return "java.lang.Character";
      } else if ((!(!(arg0 && arg0.$classData)))) {
        return arg0.$classData.N;
      } else {
        return null.sv();
      }
    }
  }
}
function $dp_codePointAt__I__I(instance, x0) {
  if (((typeof instance) === "string")) {
    return $f_T__codePointAt__I__I(instance, x0);
  } else {
    return instance.sr(x0);
  }
}
function $dp_compareTo__O__I(instance, x0) {
  switch ((typeof instance)) {
    case "string": {
      return $f_T__compareTo__O__I(instance, x0);
    }
    case "number": {
      return $f_jl_Double__compareTo__O__I(instance, x0);
    }
    case "boolean": {
      return $f_jl_Boolean__compareTo__O__I(instance, x0);
    }
    default: {
      if ((instance instanceof $Long)) {
        return $f_jl_Long__compareTo__O__I(instance.l, instance.h, x0);
      } else if ((instance instanceof $Char)) {
        return $f_jl_Character__compareTo__O__I(instance.c, x0);
      } else {
        return instance.ks(x0);
      }
    }
  }
}
function $dp_compareTo__T__I(instance, x0) {
  if (((typeof instance) === "string")) {
    return $f_T__compareTo__T__I(instance, x0);
  } else {
    return instance.ss(x0);
  }
}
function $dp_equals__O__Z(instance, x0) {
  switch ((typeof instance)) {
    case "string": {
      return $f_T__equals__O__Z(instance, x0);
    }
    case "number": {
      return $f_jl_Double__equals__O__Z(instance, x0);
    }
    case "boolean": {
      return $f_jl_Boolean__equals__O__Z(instance, x0);
    }
    case "undefined": {
      return $f_jl_Void__equals__O__Z(instance, x0);
    }
    default: {
      if (((!(!(instance && instance.$classData))) || (instance === null))) {
        return instance.p(x0);
      } else if ((instance instanceof $Long)) {
        return $f_jl_Long__equals__O__Z(instance.l, instance.h, x0);
      } else if ((instance instanceof $Char)) {
        return $f_jl_Character__equals__O__Z(instance.c, x0);
      } else {
        return $c_O.prototype.p.call(instance, x0);
      }
    }
  }
}
function $dp_getChars__I__I__AC__I__V(instance, x0, x1, x2, x3) {
  if (((typeof instance) === "string")) {
    return $f_T__getChars__I__I__AC__I__V(instance, x0, x1, x2, x3);
  } else {
    return instance.su(x0, x1, x2, x3);
  }
}
function $dp_hashCode__I(instance) {
  switch ((typeof instance)) {
    case "string": {
      return $f_T__hashCode__I(instance);
    }
    case "number": {
      return $f_jl_Double__hashCode__I(instance);
    }
    case "boolean": {
      return $f_jl_Boolean__hashCode__I(instance);
    }
    case "undefined": {
      return $f_jl_Void__hashCode__I(instance);
    }
    default: {
      if (((!(!(instance && instance.$classData))) || (instance === null))) {
        return instance.u();
      } else if ((instance instanceof $Long)) {
        return $f_jl_Long__hashCode__I(instance.l, instance.h);
      } else if ((instance instanceof $Char)) {
        return $f_jl_Character__hashCode__I(instance.c);
      } else {
        return $c_O.prototype.u.call(instance);
      }
    }
  }
}
function $dp_indexOf__I__I(instance, x0) {
  if (((typeof instance) === "string")) {
    return $f_T__indexOf__I__I(instance, x0);
  } else {
    return instance.sw(x0);
  }
}
function $dp_repeat__I__T(instance, x0) {
  if (((typeof instance) === "string")) {
    return $f_T__repeat__I__T(instance, x0);
  } else {
    return instance.sy(x0);
  }
}
function $dp_split__T__I__AT(instance, x0, x1) {
  if (((typeof instance) === "string")) {
    return $f_T__split__T__I__AT(instance, x0, x1);
  } else {
    return instance.sA(x0, x1);
  }
}
function $dp_toString__T(instance) {
  return ((instance === (void 0)) ? "undefined" : instance.toString());
}
function $dp_trim__T(instance) {
  if (((typeof instance) === "string")) {
    return $f_T__trim__T(instance);
  } else {
    return instance.sB();
  }
}
function $checkIntDivisor(arg0) {
  if ((arg0 === 0)) {
    throw new $c_jl_ArithmeticException("/ by zero");
  } else {
    return arg0;
  }
}
function $doubleToInt(arg0) {
  return ((arg0 > 2147483647) ? 2147483647 : ((arg0 < (-2147483648)) ? (-2147483648) : (arg0 | 0)));
}
function $cToS(arg0) {
  return String.fromCharCode(arg0);
}
var $fpBitsDataView = new DataView(new ArrayBuffer(8));
function $floatToBits(arg0) {
  var dataView = $fpBitsDataView;
  dataView.setFloat32(0, arg0, true);
  return dataView.getInt32(0, true);
}
function $floatFromBits(arg0) {
  var dataView = $fpBitsDataView;
  dataView.setInt32(0, arg0, true);
  return dataView.getFloat32(0, true);
}
function $doubleToBits(arg0) {
  var dataView = $fpBitsDataView;
  return $s_RTLong__fromDoubleBits__D__O__J(arg0, dataView);
}
function $doubleFromBits(arg0) {
  var dataView = $fpBitsDataView;
  return $s_RTLong__bitsToDouble__I__I__O__D(arg0.l, arg0.h, dataView);
}
function $resolveSuperRef(arg0, arg1) {
  var getPrototypeOf = Object.getPrototyeOf;
  var getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor;
  var superProto = arg0.prototype;
  while ((superProto !== null)) {
    var desc = getOwnPropertyDescriptor(superProto, arg1);
    if ((desc !== (void 0))) {
      return desc;
    }
    superProto = getPrototypeOf(superProto);
  }
}
function $superGet(arg0, arg1, arg2) {
  var desc = $resolveSuperRef(arg0, arg2);
  if ((desc !== (void 0))) {
    var getter = desc.get;
    return ((getter !== (void 0)) ? getter.call(arg1) : getter.value);
  }
}
function $superSet(arg0, arg1, arg2, arg3) {
  var desc = $resolveSuperRef(arg0, arg2);
  if ((desc !== (void 0))) {
    var setter = desc.set;
    if ((setter !== (void 0))) {
      setter.call(arg1, arg3);
      return (void 0);
    }
  }
  throw new TypeError((("super has no setter '" + arg2) + "'."));
}
function $arraycopyGeneric(arg0, arg1, arg2, arg3, arg4) {
  if (((arg0 !== arg2) || (((arg3 - arg1) >>> 0) > (arg4 >>> 0)))) {
    for (var i = 0; (i < arg4); i = ((i + 1) | 0)) {
      arg2[((arg3 + i) | 0)] = arg0[((arg1 + i) | 0)];
    }
  } else {
    for (var i = ((arg4 - 1) | 0); (i >= 0); i = ((i - 1) | 0)) {
      arg2[((arg3 + i) | 0)] = arg0[((arg1 + i) | 0)];
    }
  }
}
var $lastIDHash = 0;
var $idHashCodeMap = new WeakMap();
function $systemIdentityHashCode(obj) {
  switch ((typeof obj)) {
    case "string": {
      return $f_T__hashCode__I(obj);
    }
    case "number": {
      return $f_jl_Double__hashCode__I(obj);
    }
    case "bigint": {
      var biHash = 0;
      if ((obj < BigInt(0))) {
        obj = (~obj);
      }
      while ((obj !== BigInt(0))) {
        biHash = (biHash ^ Number(BigInt.asIntN(32, obj)));
        obj = (obj >> BigInt(32));
      }
      return biHash;
    }
    case "boolean": {
      return (obj ? 1231 : 1237);
    }
    case "undefined": {
      return 0;
    }
    case "symbol": {
      var description = obj.description;
      return ((description === (void 0)) ? 0 : $f_T__hashCode__I(description));
    }
    default: {
      if ((obj === null)) {
        return 0;
      } else {
        var hash = $idHashCodeMap.get(obj);
        if ((hash === (void 0))) {
          hash = (($lastIDHash + 1) | 0);
          $lastIDHash = hash;
          $idHashCodeMap.set(obj, hash);
        }
        return hash;
      }
    }
  }
}
function $isByte(arg0) {
  return ((((typeof arg0) === "number") && (((arg0 << 24) >> 24) === arg0)) && ((1 / arg0) !== (1 / (-0))));
}
function $isShort(arg0) {
  return ((((typeof arg0) === "number") && (((arg0 << 16) >> 16) === arg0)) && ((1 / arg0) !== (1 / (-0))));
}
function $isInt(arg0) {
  return ((((typeof arg0) === "number") && ((arg0 | 0) === arg0)) && ((1 / arg0) !== (1 / (-0))));
}
function $isFloat(arg0) {
  return (((typeof arg0) === "number") && ((arg0 !== arg0) || (Math.fround(arg0) === arg0)));
}
function $bC(arg0) {
  return new $Char(arg0);
}
var $bC0 = $bC(0);
function $bL(arg0, arg1) {
  return new $Long(arg0, arg1);
}
var $bL0 = $bL(0, 0);
function $uC(arg0) {
  return ((arg0 === null) ? 0 : arg0.c);
}
function $uJ(arg0) {
  return ((arg0 === null) ? $bL0 : arg0);
}
function $ct_O__($thiz) {
  return $thiz;
}
/** @constructor */
function $c_O() {
}
$p = $c_O.prototype;
$p.constructor = $c_O;
/** @constructor */
function $h_O() {
}
$h_O.prototype = $p;
$p.u = (function() {
  return $systemIdentityHashCode(this);
});
$p.p = (function(that) {
  return (this === that);
});
$p.t = (function() {
  var i = this.u();
  return (($objectClassName(this) + "@") + (i >>> 0.0).toString(16));
});
$p.toString = (function() {
  return this.t();
});
function $ac_O(arg) {
  if (((typeof arg) === "number")) {
    this.a = new Array(arg);
    for (var i = 0; (i < arg); (i++)) {
      this.a[i] = null;
    }
  } else {
    this.a = arg;
  }
}
$p = $ac_O.prototype = new $h_O();
$p.constructor = $ac_O;
$p.s = (function(srcPos, dest, destPos, length) {
  $arraycopyGeneric(this.a, srcPos, dest.a, destPos, length);
});
$p.h = (function() {
  return new $ac_O(this.a.slice());
});
function $ah_O() {
}
$ah_O.prototype = $p;
function $ac_Z(arg) {
  if (((typeof arg) === "number")) {
    this.a = new Array(arg);
    for (var i = 0; (i < arg); (i++)) {
      this.a[i] = false;
    }
  } else {
    this.a = arg;
  }
}
$p = $ac_Z.prototype = new $h_O();
$p.constructor = $ac_Z;
$p.s = (function(srcPos, dest, destPos, length) {
  $arraycopyGeneric(this.a, srcPos, dest.a, destPos, length);
});
$p.h = (function() {
  return new $ac_Z(this.a.slice());
});
function $ac_C(arg) {
  if (((typeof arg) === "number")) {
    this.a = new Uint16Array(arg);
  } else {
    this.a = arg;
  }
}
$p = $ac_C.prototype = new $h_O();
$p.constructor = $ac_C;
$p.s = (function(srcPos, dest, destPos, length) {
  dest.a.set(this.a.subarray(srcPos, ((srcPos + length) | 0)), destPos);
});
$p.h = (function() {
  return new $ac_C(this.a.slice());
});
function $ac_B(arg) {
  if (((typeof arg) === "number")) {
    this.a = new Int8Array(arg);
  } else {
    this.a = arg;
  }
}
$p = $ac_B.prototype = new $h_O();
$p.constructor = $ac_B;
$p.s = (function(srcPos, dest, destPos, length) {
  dest.a.set(this.a.subarray(srcPos, ((srcPos + length) | 0)), destPos);
});
$p.h = (function() {
  return new $ac_B(this.a.slice());
});
function $ac_S(arg) {
  if (((typeof arg) === "number")) {
    this.a = new Int16Array(arg);
  } else {
    this.a = arg;
  }
}
$p = $ac_S.prototype = new $h_O();
$p.constructor = $ac_S;
$p.s = (function(srcPos, dest, destPos, length) {
  dest.a.set(this.a.subarray(srcPos, ((srcPos + length) | 0)), destPos);
});
$p.h = (function() {
  return new $ac_S(this.a.slice());
});
function $ac_I(arg) {
  if (((typeof arg) === "number")) {
    this.a = new Int32Array(arg);
  } else {
    this.a = arg;
  }
}
$p = $ac_I.prototype = new $h_O();
$p.constructor = $ac_I;
$p.s = (function(srcPos, dest, destPos, length) {
  dest.a.set(this.a.subarray(srcPos, ((srcPos + length) | 0)), destPos);
});
$p.h = (function() {
  return new $ac_I(this.a.slice());
});
function $ac_J(arg) {
  if (((typeof arg) === "number")) {
    arg = (arg << 1);
    this.a = new Int32Array(arg);
  } else {
    this.a = arg;
  }
}
$p = $ac_J.prototype = new $h_O();
$p.constructor = $ac_J;
$p.s = (function(srcPos, dest, destPos, length) {
  dest.a.set(this.a.subarray((srcPos << 1), (((srcPos + length) | 0) << 1)), (destPos << 1));
});
$p.h = (function() {
  return new $ac_J(this.a.slice());
});
function $ac_F(arg) {
  if (((typeof arg) === "number")) {
    this.a = new Float32Array(arg);
  } else {
    this.a = arg;
  }
}
$p = $ac_F.prototype = new $h_O();
$p.constructor = $ac_F;
$p.s = (function(srcPos, dest, destPos, length) {
  dest.a.set(this.a.subarray(srcPos, ((srcPos + length) | 0)), destPos);
});
$p.h = (function() {
  return new $ac_F(this.a.slice());
});
function $ac_D(arg) {
  if (((typeof arg) === "number")) {
    this.a = new Float64Array(arg);
  } else {
    this.a = arg;
  }
}
$p = $ac_D.prototype = new $h_O();
$p.constructor = $ac_D;
$p.s = (function(srcPos, dest, destPos, length) {
  dest.a.set(this.a.subarray(srcPos, ((srcPos + length) | 0)), destPos);
});
$p.h = (function() {
  return new $ac_D(this.a.slice());
});
function $TypeData() {
  this.C = (void 0);
  this.n = null;
  this.O = null;
  this.B = null;
  this.D = 0;
  this.z = null;
  this.E = "";
  this.L = (void 0);
  this.A = (void 0);
  this.F = (void 0);
  this.w = (void 0);
  this.J = false;
  this.N = "";
  this.X = false;
  this.Y = false;
  this.Z = false;
  this.I = (void 0);
}
$p = $TypeData.prototype;
$p.p = (function(zero, arrayEncodedName, displayName, arrayClass, typedArrayClass) {
  this.n = ({});
  this.z = zero;
  this.E = arrayEncodedName;
  var self = this;
  this.F = ((that) => (that === self));
  this.N = displayName;
  this.X = true;
  this.I = ((obj) => false);
  if ((arrayClass !== (void 0))) {
    this.A = new $TypeData().y(this, arrayClass, typedArrayClass, (arrayEncodedName === "J"));
  }
  return this;
});
$p.i = (function(kindOrCtor, fullName, ancestors, isInstance) {
  var internalName = Object.getOwnPropertyNames(ancestors)[0];
  this.n = ancestors;
  this.E = (("L" + fullName) + ";");
  this.F = ((that) => (!(!that.n[internalName])));
  this.J = (kindOrCtor === 2);
  this.N = fullName;
  this.Y = (kindOrCtor === 1);
  this.I = (isInstance || ((obj) => (!(!((obj && obj.$classData) && obj.$classData.n[internalName])))));
  if (((typeof kindOrCtor) !== "number")) {
    kindOrCtor.prototype.$classData = this;
  }
  return this;
});
$p.y = (function(componentData, arrayClass, typedArrayClass, isLongArray, isAssignableFromFun) {
  arrayClass.prototype.$classData = this;
  var name = ("[" + componentData.E);
  this.C = arrayClass;
  this.n = ({
    E: 1,
    a: 1
  });
  this.O = componentData;
  this.B = componentData;
  this.D = 1;
  this.E = name;
  this.N = name;
  this.Z = true;
  var self = this;
  this.F = (isAssignableFromFun || ((that) => (self === that)));
  this.w = (isLongArray ? ((array) => {
    var len = (array.length | 0);
    var result = new arrayClass(len);
    var u = result.a;
    for (var i = 0; (i < len); i = ((i + 1) | 0)) {
      var srcElem = array[i];
      u[(i << 1)] = srcElem.l;
      u[(((i << 1) + 1) | 0)] = srcElem.h;
    }
    return result;
  }) : (typedArrayClass ? ((array) => new arrayClass(new typedArrayClass(array))) : ((array) => new arrayClass(array))));
  this.I = ((obj) => (obj instanceof arrayClass));
  return this;
});
$p.a = (function(componentData) {
  function ArrayClass(arg) {
    if (((typeof arg) === "number")) {
      this.a = new Array(arg);
      for (var i = 0; (i < arg); (i++)) {
        this.a[i] = null;
      }
    } else {
      this.a = arg;
    }
  }
  var $p = ArrayClass.prototype = new $ah_O();
  $p.constructor = ArrayClass;
  $p.s = (function(srcPos, dest, destPos, length) {
    $arraycopyGeneric(this.a, srcPos, dest.a, destPos, length);
  });
  $p.h = (function() {
    return new ArrayClass(this.a.slice());
  });
  $p.$classData = this;
  var arrayBase = (componentData.B || componentData);
  var arrayDepth = (componentData.D + 1);
  var name = ("[" + componentData.E);
  this.C = ArrayClass;
  this.n = ({
    E: 1,
    a: 1
  });
  this.O = componentData;
  this.B = arrayBase;
  this.D = arrayDepth;
  this.E = name;
  this.N = name;
  this.Z = true;
  var isAssignableFromFun = ((that) => {
    var thatDepth = that.D;
    return ((thatDepth === arrayDepth) ? arrayBase.F(that.B) : ((thatDepth > arrayDepth) && (arrayBase === $d_O)));
  });
  this.F = isAssignableFromFun;
  this.w = ((array) => new ArrayClass(array));
  var self = this;
  this.I = ((obj) => {
    var data = (obj && obj.$classData);
    return ((!(!data)) && ((data === self) || isAssignableFromFun(data)));
  });
  return this;
});
$p.r = (function() {
  if ((!this.A)) {
    this.A = new $TypeData().a(this);
  }
  return this.A;
});
$p.l = (function() {
  if ((!this.L)) {
    this.L = new $c_jl_Class(this);
  }
  return this.L;
});
$p.R = (function(that) {
  return ((this === that) || this.F(that));
});
$p.S = (function() {
  return (this.P ? this.P.l() : null);
});
$p.Q = (function() {
  return (this.O ? this.O.l() : null);
});
$p.U = (function(length) {
  if ((this === $d_V)) {
    throw $ct_jl_IllegalArgumentException__(new $c_jl_IllegalArgumentException());
  }
  return new (this.r().C)(length);
});
function $isArrayOf_O(obj, depth) {
  var data = (obj && obj.$classData);
  if ((!data)) {
    return false;
  } else {
    var arrayDepth = data.D;
    return ((arrayDepth === depth) ? (!data.B.X) : (arrayDepth > depth));
  }
}
function $isArrayOf_Z(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && (obj.$classData.B === $d_Z))));
}
function $isArrayOf_C(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && (obj.$classData.B === $d_C))));
}
function $isArrayOf_B(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && (obj.$classData.B === $d_B))));
}
function $isArrayOf_S(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && (obj.$classData.B === $d_S))));
}
function $isArrayOf_I(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && (obj.$classData.B === $d_I))));
}
function $isArrayOf_J(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && (obj.$classData.B === $d_J))));
}
function $isArrayOf_F(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && (obj.$classData.B === $d_F))));
}
function $isArrayOf_D(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && (obj.$classData.B === $d_D))));
}
var $d_O = new $TypeData();
$d_O.n = ({});
$d_O.E = "Ljava.lang.Object;";
$d_O.F = ((that) => (!that.X));
$d_O.N = "java.lang.Object";
$d_O.I = ((obj) => (obj !== null));
$d_O.A = new $TypeData().y($d_O, $ac_O, (void 0), false, ((that) => {
  var thatDepth = that.D;
  return ((thatDepth === 1) ? (!that.B.X) : (thatDepth > 1));
}));
$c_O.prototype.$classData = $d_O;
var $d_V = new $TypeData().p((void 0), "V", "void", (void 0), (void 0));
var $d_Z = new $TypeData().p(false, "Z", "boolean", $ac_Z, (void 0));
var $d_C = new $TypeData().p(0, "C", "char", $ac_C, Uint16Array);
var $d_B = new $TypeData().p(0, "B", "byte", $ac_B, Int8Array);
var $d_S = new $TypeData().p(0, "S", "short", $ac_S, Int16Array);
var $d_I = new $TypeData().p(0, "I", "int", $ac_I, Int32Array);
var $d_J = new $TypeData().p($bL0, "J", "long", $ac_J, Int32Array);
var $d_F = new $TypeData().p(0.0, "F", "float", $ac_F, Float32Array);
var $d_D = new $TypeData().p(0.0, "D", "double", $ac_D, Float64Array);
var $typedArraysAreBigEndian = (new Int8Array(new Int32Array([1]).buffer)[0] === 0);
function $constArrayBuffer_B(len, encoded) {
  var buf = new ArrayBuffer(len);
  var view = new DataView(buf);
  var regularChunksEnd = ((encoded.length - 4) | 0);
  var i = 0;
  var j = 0;
  var chunk = 0;
  while (true) {
    chunk = (((encoded.charCodeAt(i) | (encoded.charCodeAt(((i + 1) | 0)) << 8)) | (encoded.charCodeAt(((i + 2) | 0)) << 16)) | (encoded.charCodeAt(((i + 3) | 0)) << 24));
    chunk = ((((chunk - 808464432) | 0) - ((chunk & 1616928864) >>> 3)) | 0);
    chunk = (((chunk & 1056980736) >>> 2) | (chunk & 4128831));
    chunk = (((chunk & 268369920) >>> 4) | (chunk & 4095));
    if ((i === regularChunksEnd)) {
      break;
    }
    view.setUint32(j, chunk, true);
    i = ((i + 4) | 0);
    j = ((j + 3) | 0);
  }
  var trailing = ((len - j) | 0);
  view.setUint8(j, chunk);
  if ((trailing !== 1)) {
    view.setUint8(((j + 1) | 0), (chunk >>> 8));
    if ((trailing === 3)) {
      view.setUint8(((j + 2) | 0), (chunk >>> 16));
    }
  }
  return buf;
}
function $constArrayBuffer_S(len, encoded) {
  var buf = $constArrayBuffer_B((len << 1), encoded);
  if ($typedArraysAreBigEndian) {
    var view = new DataView(buf);
    var i = 0;
    while ((i !== len)) {
      view.putInt16(i, view.getInt16(i, true), false);
      i = ((i + 2) | 0);
    }
  }
  return buf;
}
function $constArrayBuffer_I(len, encoded) {
  var buf = $constArrayBuffer_B((len << 2), encoded);
  if ($typedArraysAreBigEndian) {
    var view = new DataView(buf);
    var i = 0;
    while ((i !== len)) {
      view.putInt32(i, view.getInt32(i, true), false);
      i = ((i + 4) | 0);
    }
  }
  return buf;
}
function $constArrayBuffer_J(len, encoded) {
  return $constArrayBuffer_I((len << 1), encoded);
}
function $constTypedArrayU_I(len, encoded, prevMask) {
  var buf = new Int32Array(len);
  var inLen = (encoded.length | 0);
  var prev = 0;
  var i = 0;
  var j = 0;
  var v = 0;
  while ((i !== inLen)) {
    var c = encoded.charCodeAt(i);
    if ((c < 80)) {
      v = ((v | (c - 48)) << 5);
    } else {
      v = (v | (c - 93));
      prev = (((prev & prevMask) + v) | 0);
      buf[j] = prev;
      j = ((j + 1) | 0);
      v = 0;
    }
    i = ((i + 1) | 0);
  }
  return buf;
}
function $constTypedArrayS_I(len, encoded, prevMask) {
  var buf = new Int32Array(len);
  var inLen = (encoded.length | 0);
  var prev = 0;
  var i = 0;
  var j = 0;
  var v = 0;
  var first = true;
  while ((i !== inLen)) {
    var c = encoded.charCodeAt(i);
    if ((c < 80)) {
      if (first) {
        v = (((c - 48) << 27) >> 22);
        first = false;
      } else {
        v = ((v | (c - 48)) << 5);
      }
    } else {
      if (first) {
        v = (((c - 93) << 27) >> 27);
      } else {
        v = (v | (c - 93));
        first = true;
      }
      prev = (((prev & prevMask) + v) | 0);
      buf[j] = prev;
      j = ((j + 1) | 0);
    }
    i = ((i + 1) | 0);
  }
  return buf;
}
function $constArrRaw_B(len, encoded) {
  return new $ac_B(new Int8Array($constArrayBuffer_B(len, encoded)));
}
function $constArrRaw_S(len, encoded) {
  return new $ac_S(new Int16Array($constArrayBuffer_S(len, encoded)));
}
function $constArrRaw_C(len, encoded) {
  return new $ac_C(new Uint16Array($constArrayBuffer_S(len, encoded)));
}
function $constArrRaw_I(len, encoded) {
  return new $ac_I(new Int32Array($constArrayBuffer_I(len, encoded)));
}
function $constArrRaw_J(len, encoded) {
  return new $ac_J(new Int32Array($constArrayBuffer_J(len, encoded)));
}
function $constArrUVals_I(len, encoded) {
  return new $ac_I($constTypedArrayU_I(len, encoded, 0));
}
function $constArrUDiffs_I(len, encoded) {
  return new $ac_I($constTypedArrayU_I(len, encoded, (-1)));
}
function $constArrSVals_I(len, encoded) {
  return new $ac_I($constTypedArrayS_I(len, encoded, 0));
}
function $constArrSDiffs_I(len, encoded) {
  return new $ac_I($constTypedArrayS_I(len, encoded, (-1)));
}
function $constArrUVals_J(len, encoded) {
  return new $ac_J($constTypedArrayU_I((len << 1), encoded, 0));
}
function $constArrUDiffs_J(len, encoded) {
  return new $ac_J($constTypedArrayU_I((len << 1), encoded, (-1)));
}
function $constArrSVals_J(len, encoded) {
  return new $ac_J($constTypedArrayS_I((len << 1), encoded, 0));
}
function $constArrSDiffs_J(len, encoded) {
  return new $ac_J($constTypedArrayS_I((len << 1), encoded, (-1)));
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__getScastieConfiguration__sjs_js_Dynamic($thiz) {
  var s = scastieConfiguration;
  return ({
    "sbtConfig": s,
    "targetType": "scala3"
  });
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__getButtonsSection__Lorg_scalajs_dom_HTMLElement__s_Option($thiz, snippet) {
  var x1 = snippet.querySelector("div.buttons");
  if ((!(!(x1 instanceof HTMLDivElement)))) {
    return new $c_s_Some(x1);
  }
  return $m_s_None$();
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__handleHideableCode__Lorg_scalajs_dom_HTMLElement__V($thiz, snippet) {
  $p_Ldotty_tools_scaladoc_CodeSnippets__toggleHide$1__Lorg_scalajs_dom_ParentNode__V($thiz, snippet);
  var buttonsSection = $p_Ldotty_tools_scaladoc_CodeSnippets__getButtonsSection__Lorg_scalajs_dom_HTMLElement__s_Option($thiz, snippet);
  var hideables = snippet.querySelectorAll(".hideable");
  if (((hideables !== null) && (!$f_sc_SeqOps__isEmpty__Z(new $c_Lorg_scalajs_dom_DOMList$DOMListSeq(hideables))))) {
    var showHideButton = $p_Ldotty_tools_scaladoc_CodeSnippets__createShowHideButton$1__Lorg_scalajs_dom_HTMLElement__Lorg_scalajs_dom_HTMLDivElement($thiz, snippet);
    if ((!buttonsSection.g())) {
      var x0 = buttonsSection.S();
      x0.appendChild(showHideButton);
    }
  }
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__snippetAnchor__Lorg_scalajs_dom_HTMLElement__V($thiz, snippet) {
  var x7 = snippet.querySelector(".snippet-meta .snippet-label");
  if ((!(!(x7 instanceof HTMLElement)))) {
    var name = $f_T__trim__T(x7.textContent);
    var $x_1 = $m_Ldotty_tools_scaladoc_utils_HTML$().dZ;
    var this$1 = $m_Ldotty_tools_scaladoc_utils_HTML$().eu;
    var value = ("snippet-" + name);
    var anchor = $x_1.cm($ct_T2__O__O__(new $c_T2(), this$1.v, value), new $c_sjsr_WrappedVarArgs([]));
    snippet.insertBefore(anchor, snippet.firstChild);
    return (void 0);
  }
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__handleImportedCode__Lorg_scalajs_dom_HTMLElement__V($thiz, snippet) {
  var included = snippet.querySelectorAll("code span.include");
  var pre = snippet.querySelector("pre");
  if ((((included !== null) && (!$f_sc_SeqOps__isEmpty__Z(new $c_Lorg_scalajs_dom_DOMList$DOMListSeq(included)))) && (pre !== null))) {
    var this$6 = $f_sc_IterableOps__collect__s_PartialFunction__O(new $c_Lorg_scalajs_dom_DOMList$DOMListSeq(included), new $c_Ldotty_tools_scaladoc_CodeSnippets$$anon$1()).c2();
    var f = ((_$4$2) => (!(!_$4$2.hasAttribute("name"))));
    _return: {
      var result;
      var l$tailLocal1 = this$6;
      while (true) {
        if (l$tailLocal1.g()) {
          var result = $m_sci_Nil$();
          break;
        } else {
          var h = l$tailLocal1.m();
          var t = l$tailLocal1.o();
          if ((!(!(!f(h))))) {
            l$tailLocal1 = t;
            continue;
          }
          var start = l$tailLocal1;
          var remaining$tailLocal1 = t;
          while (true) {
            if (remaining$tailLocal1.g()) {
              var result = start;
              break _return;
            } else {
              var x = remaining$tailLocal1.m();
              if ((!(!f(x)))) {
                remaining$tailLocal1 = remaining$tailLocal1.o();
                continue;
              }
              var firstMiss = remaining$tailLocal1;
              var newHead = new $c_sci_$colon$colon(start.m(), $m_sci_Nil$());
              var toProcess = start.o();
              var currentLast = newHead;
              while ((toProcess !== firstMiss)) {
                var newElem = new $c_sci_$colon$colon(toProcess.m(), $m_sci_Nil$());
                currentLast.K = newElem;
                currentLast = newElem;
                toProcess = toProcess.o();
              }
              var next = firstMiss.o();
              var nextToCopy = next;
              while ((!next.g())) {
                var head = next.m();
                if ((!(!f(head)))) {
                  next = next.o();
                } else {
                  while ((nextToCopy !== next)) {
                    var newElem$2 = new $c_sci_$colon$colon(nextToCopy.m(), $m_sci_Nil$());
                    currentLast.K = newElem$2;
                    currentLast = newElem$2;
                    nextToCopy = nextToCopy.o();
                  }
                  nextToCopy = next.o();
                  next = next.o();
                }
              }
              if ((!nextToCopy.g())) {
                currentLast.K = nextToCopy;
              }
              var result = newHead;
              break _return;
            }
          }
        }
      }
    }
    var f$1 = ((_$5$2) => _$5$2.getAttribute("name"));
    if ((result === $m_sci_Nil$())) {
      var $x_1 = $m_sci_Nil$();
    } else {
      var x0 = result.m();
      var h$1 = new $c_sci_$colon$colon(f$1(x0), $m_sci_Nil$());
      var t$1 = h$1;
      var rest = result.o();
      while ((rest !== $m_sci_Nil$())) {
        var x0$1 = rest.m();
        var nx = new $c_sci_$colon$colon(f$1(x0$1), $m_sci_Nil$());
        t$1.K = nx;
        t$1 = nx;
        rest = rest.o();
      }
      var $x_1 = h$1;
    }
    var this$23 = $f_sc_SeqOps__distinct__O($x_1);
    var f$2 = ((name$2) => {
      var $x_3 = $m_Ldotty_tools_scaladoc_utils_HTML$().dZ;
      var $x_2 = $ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "unselectable");
      var this$12 = $m_Ldotty_tools_scaladoc_utils_HTML$().fY;
      var value = ("#snippet-" + name$2);
      return $x_3.D(new $c_sjsr_WrappedVarArgs([$x_2, $ct_T2__O__O__(new $c_T2(), this$12.v, value)]), new $c_sjsr_WrappedVarArgs(["included", $m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs([name$2]))]));
    });
    if ((this$23 === $m_sci_Nil$())) {
      var $x_4 = $m_sci_Nil$();
    } else {
      var x0$3 = this$23.m();
      var h$2 = new $c_sci_$colon$colon(f$2(x0$3), $m_sci_Nil$());
      var t$2 = h$2;
      var rest$1 = this$23.o();
      while ((rest$1 !== $m_sci_Nil$())) {
        var x0$4 = rest$1.m();
        var nx$1 = new $c_sci_$colon$colon(f$2(x0$4), $m_sci_Nil$());
        t$2.K = nx$1;
        t$2 = nx$1;
        rest$1 = rest$1.o();
      }
      var $x_4 = h$2;
    }
    var includes = $x_4;
    var includesDiv = $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "included-section hideable")]), new $c_sjsr_WrappedVarArgs([includes]));
    snippet.insertBefore(includesDiv, pre);
  }
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__copyRunButtons__Lorg_scalajs_dom_HTMLElement__V($thiz, snippet) {
  var buttonsSection = $p_Ldotty_tools_scaladoc_CodeSnippets__getButtonsSection__Lorg_scalajs_dom_HTMLElement__s_Option($thiz, snippet);
  if ((!buttonsSection.g())) {
    var x0 = buttonsSection.S();
    x0.appendChild($p_Ldotty_tools_scaladoc_CodeSnippets__copyButton$1__Lorg_scalajs_dom_HTMLElement__Lorg_scalajs_dom_HTMLDivElement($thiz, snippet));
    if ((!(!snippet.hasAttribute("runnable")))) {
      x0.appendChild($p_Ldotty_tools_scaladoc_CodeSnippets__runButton$1__Lorg_scalajs_dom_HTMLElement__Lorg_scalajs_dom_HTMLDivElement($thiz, snippet));
    }
  }
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__toggleHide$1__Lorg_scalajs_dom_ParentNode__V($thiz, e) {
  var this$2 = new $c_Lorg_scalajs_dom_DOMList$DOMListSeq(e.querySelectorAll(".hideable"));
  var it = this$2.c();
  while (it.k()) {
    var x0 = it.f();
    if ((!(!(x0 instanceof HTMLElement)))) {
      (!(!x0.classList.toggle("hidden")));
    }
  }
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__createShowHideButton$1__Lorg_scalajs_dom_HTMLElement__Lorg_scalajs_dom_HTMLDivElement($thiz, toggleRoot) {
  return $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "snippet-showhide-container")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().lz.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "snippet-showhide-button")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_s_util_ChainingOps$().f4($m_Ldotty_tools_scaladoc_utils_HTML$().jh.cm(($m_Ldotty_tools_scaladoc_utils_HTML$(), $ct_T2__O__O__(new $c_T2(), "type", "checkbox")), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "snippet-showhide")])))), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$1$3) => {
    _$1$3.addEventListener("change", ((_$2$2) => {
      $p_Ldotty_tools_scaladoc_CodeSnippets__toggleHide$1__Lorg_scalajs_dom_ParentNode__V($thiz, toggleRoot);
    }));
  }))), $m_Ldotty_tools_scaladoc_utils_HTML$().b0.cm($ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "snippet-showhide-text"), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([]))))]))))]))));
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__copyButton$1__Lorg_scalajs_dom_HTMLElement__Lorg_scalajs_dom_HTMLDivElement($thiz, snippet$1) {
  var $x_1 = $m_Ldotty_tools_scaladoc_utils_HTML$().ay;
  var a = $m_Ldotty_tools_scaladoc_utils_HTML$().i2.cm($ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "copy-button icon-button"), new $c_sjsr_WrappedVarArgs([]));
  a.addEventListener("click", ((_$7$2) => {
    var code = $f_sc_IterableOnceOps__mkString__T__T__T__T($f_sc_IterableOps__map__F1__O(new $c_Lorg_scalajs_dom_DOMList$DOMListSeq(snippet$1.querySelectorAll("code>span:not(.hidden)")), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$8$2) => _$8$2.textContent))), "", "", "");
    return window.navigator.clipboard.writeText(code);
  }));
  return $x_1.Z(new $c_sjsr_WrappedVarArgs([a]));
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__handler$lzyINIT1$1__sr_LazyRef__Lorg_scalajs_dom_HTMLDivElement__F1($thiz, handler$lzy1$1, popup$1) {
  return (handler$lzy1$1.iE ? handler$lzy1$1.iF : handler$lzy1$1.rd(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((e$3) => {
    if ((e$3.fromPopup === (void 0))) {
      document.body.removeChild(popup$1);
      var \u03b41$ = document.body;
      \u03b41$.removeEventListener("click", $m_sjs_js_Any$().kM($p_Ldotty_tools_scaladoc_CodeSnippets__handler$1__sr_LazyRef__Lorg_scalajs_dom_HTMLDivElement__F1($thiz, handler$lzy1$1, popup$1)));
    }
  }))));
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__handler$1__sr_LazyRef__Lorg_scalajs_dom_HTMLDivElement__F1($thiz, handler$lzy1$3, popup$3) {
  return (handler$lzy1$3.iE ? handler$lzy1$3.iF : $p_Ldotty_tools_scaladoc_CodeSnippets__handler$lzyINIT1$1__sr_LazyRef__Lorg_scalajs_dom_HTMLDivElement__F1($thiz, handler$lzy1$3, popup$3));
}
function $p_Ldotty_tools_scaladoc_CodeSnippets__runButton$1__Lorg_scalajs_dom_HTMLElement__Lorg_scalajs_dom_HTMLDivElement($thiz, snippet$4) {
  var runButton = $m_Ldotty_tools_scaladoc_utils_HTML$().i2.D(new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "run-button icon-button")]), new $c_sjsr_WrappedVarArgs([$m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs(["\u25b6"]))]));
  runButton.addEventListener("click", ((e$2) => {
    var handler$lzy1 = new $c_sr_LazyRef();
    var $x_2 = $m_Ldotty_tools_scaladoc_utils_HTML$().ay;
    var $x_1 = new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "snippet-popup")]);
    var a = $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "snippet-popup-content body-small")]), new $c_sjsr_WrappedVarArgs([$m_Ldotty_tools_scaladoc_utils_HTML$().lB.Z(new $c_sjsr_WrappedVarArgs([$m_Ldotty_tools_scaladoc_utils_HTML$().lw.Z(new $c_sjsr_WrappedVarArgs([snippet$4.querySelector("pre").textContent]))]))]));
    a.addEventListener("click", ((e$2$1) => {
      e$2$1.fromPopup = true;
    }));
    var popup = $x_2.D($x_1, new $c_sjsr_WrappedVarArgs([a]));
    document.body.appendChild(popup);
    var \u03b42$ = document.body;
    \u03b42$.addEventListener("click", $m_sjs_js_Any$().kM($p_Ldotty_tools_scaladoc_CodeSnippets__handler$1__sr_LazyRef__Lorg_scalajs_dom_HTMLDivElement__F1($thiz, handler$lzy1, popup)));
    scastie.Embedded(popup.querySelector("pre"), $thiz.rZ());
    popup.querySelector("li.btn.run-button").click();
    e$2.stopPropagation();
  }));
  return $m_Ldotty_tools_scaladoc_utils_HTML$().ay.Z(new $c_sjsr_WrappedVarArgs([runButton]));
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_CodeSnippets() {
  this.le = null;
  this.lf = false;
  window.addEventListener("dynamicPageLoad", ((e$2) => {
    this.qy();
  }));
}
$p = $c_Ldotty_tools_scaladoc_CodeSnippets.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_CodeSnippets;
/** @constructor */
function $h_Ldotty_tools_scaladoc_CodeSnippets() {
}
$h_Ldotty_tools_scaladoc_CodeSnippets.prototype = $p;
$p.rZ = (function() {
  if ((!this.lf)) {
    this.le = $p_Ldotty_tools_scaladoc_CodeSnippets__getScastieConfiguration__sjs_js_Dynamic(this);
    this.lf = true;
  }
  return this.le;
});
$p.qy = (function() {
  var this$2 = new $c_Lorg_scalajs_dom_DOMList$DOMListSeq(document.querySelectorAll("div.snippet[scala-snippet]"));
  var it = this$2.c();
  while (it.k()) {
    var x0 = it.f();
    matchResult2: {
      if ((!(!(x0 instanceof HTMLElement)))) {
        x0.addEventListener("click", ((e$2) => {
          e$2.fromSnippet = true;
        }));
        $p_Ldotty_tools_scaladoc_CodeSnippets__snippetAnchor__Lorg_scalajs_dom_HTMLElement__V(this, x0);
        $p_Ldotty_tools_scaladoc_CodeSnippets__handleHideableCode__Lorg_scalajs_dom_HTMLElement__V(this, x0);
        $p_Ldotty_tools_scaladoc_CodeSnippets__handleImportedCode__Lorg_scalajs_dom_HTMLElement__V(this, x0);
        $p_Ldotty_tools_scaladoc_CodeSnippets__copyRunButtons__Lorg_scalajs_dom_HTMLElement__V(this, x0);
        break matchResult2;
      }
      throw new $c_s_MatchError(x0);
    }
  }
});
var $d_Ldotty_tools_scaladoc_CodeSnippets = new $TypeData().i($c_Ldotty_tools_scaladoc_CodeSnippets, "dotty.tools.scaladoc.CodeSnippets", ({
  d2: 1
}));
function $p_Ldotty_tools_scaladoc_DropdownHandler__addVersionsList__T__V($thiz, json) {
  var ver = JSON.parse(json);
  var ddc = document.getElementById("version-dropdown");
  var dict = ver.versions;
  var it = new $c_sjs_js_WrappedDictionary$DictionaryIterator(dict);
  while (it.k()) {
    var x0 = it.gw();
    matchResult1: {
      if ((x0 !== null)) {
        var k = x0.aw();
        var v = x0.ap();
        var child = $m_Ldotty_tools_scaladoc_utils_HTML$().dZ.D(new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "text-button"), $ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().fY.v, v)]), new $c_sjsr_WrappedVarArgs([k]));
        ddc.appendChild(child);
        break matchResult1;
      }
      throw new $c_s_MatchError(x0);
    }
  }
}
function $p_Ldotty_tools_scaladoc_DropdownHandler__disableButton__V($thiz) {
  var btn = document.getElementById("dropdown-trigger");
  btn.classList.add("disabled");
  btn.classList.add("hidden");
}
function $p_Ldotty_tools_scaladoc_DropdownHandler__getURLContent__T__s_concurrent_Future($thiz, url) {
  return $m_sjs_js_Thenable$Implicits$().p0(fetch(url)).qN(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$1$2) => $m_sjs_js_Thenable$Implicits$().p0(_$1$2.text()))), $m_s_concurrent_ExecutionContext$().iX());
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_DropdownHandler() {
  this.gD = null;
  this.hX = null;
  this.gD = "versions-json";
  this.hX = "undefined_versions";
  matchResult5: {
    var x12 = window.sessionStorage.getItem(this.gD);
    if ((x12 === null)) {
      if (((typeof versionsDictionaryUrl) === "undefined")) {
        window.sessionStorage.setItem(this.gD, this.hX);
        $p_Ldotty_tools_scaladoc_DropdownHandler__disableButton__V(this);
      } else {
        $p_Ldotty_tools_scaladoc_DropdownHandler__getURLContent__T__s_concurrent_Future(this, versionsDictionaryUrl).oC(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$3) => {
          matchResult2: {
            if ((x$1$3 instanceof $c_s_util_Success)) {
              var x8 = x$1$3.fx;
              if ((x8 !== null)) {
                window.sessionStorage.setItem(this.gD, x8);
                $p_Ldotty_tools_scaladoc_DropdownHandler__addVersionsList__T__V(this, x8);
                break matchResult2;
              }
            }
            if ((x$1$3 instanceof $c_s_util_Failure)) {
              window.sessionStorage.setItem(this.gD, this.hX);
              $p_Ldotty_tools_scaladoc_DropdownHandler__disableButton__V(this);
              break matchResult2;
            }
            throw new $c_s_MatchError(x$1$3);
          }
        })), $m_s_concurrent_ExecutionContext$().iX());
      }
      break matchResult5;
    }
    if ((this.hX === x12)) {
      $p_Ldotty_tools_scaladoc_DropdownHandler__disableButton__V(this);
    } else {
      $p_Ldotty_tools_scaladoc_DropdownHandler__addVersionsList__T__V(this, x12);
    }
  }
  document.addEventListener("click", ((e$2) => {
    document.getElementById("version-dropdown").classList.remove("expanded");
    document.getElementById("dropdown-trigger").classList.remove("selected");
  }));
  var \u03b41$ = document.getElementById("version-dropdown");
  \u03b41$.addEventListener("click", ((e$2$1) => {
    e$2$1.stopPropagation();
  }));
}
$p = $c_Ldotty_tools_scaladoc_DropdownHandler.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_DropdownHandler;
/** @constructor */
function $h_Ldotty_tools_scaladoc_DropdownHandler() {
}
$h_Ldotty_tools_scaladoc_DropdownHandler.prototype = $p;
var $d_Ldotty_tools_scaladoc_DropdownHandler = new $TypeData().i($c_Ldotty_tools_scaladoc_DropdownHandler, "dotty.tools.scaladoc.DropdownHandler", ({
  d4: 1
}));
/** @constructor */
function $c_Ldotty_tools_scaladoc_DropdownHandler$package$() {
}
$p = $c_Ldotty_tools_scaladoc_DropdownHandler$package$.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_DropdownHandler$package$;
/** @constructor */
function $h_Ldotty_tools_scaladoc_DropdownHandler$package$() {
}
$h_Ldotty_tools_scaladoc_DropdownHandler$package$.prototype = $p;
$p.qw = (function(e) {
  e.stopPropagation();
  if (((new $c_Lorg_scalajs_dom_DOMList$DOMListSeq(document.getElementById("version-dropdown").getElementsByTagName("a")).l() > 0) && ($dp_toString__T(window.getSelection()).length === 0))) {
    document.getElementById("version-dropdown").classList.toggle("expanded");
    document.getElementById("dropdown-trigger").classList.toggle("selected");
  }
});
$p.qJ = (function() {
  var input = document.getElementById("dropdown-input");
  var this$1 = input.value;
  var filter = this$1.toUpperCase();
  var div = document.getElementById("version-dropdown");
  var as = div.getElementsByTagName("a");
  var this$3 = new $c_Lorg_scalajs_dom_DOMList$DOMListSeq(as);
  var it = this$3.c();
  while (it.k()) {
    var x0 = it.f();
    var txtValue = x0.innerText;
    var cl = x0.classList;
    var this$4 = txtValue.toUpperCase();
    if (((this$4.indexOf(filter) | 0) > (-1))) {
      cl.remove("filtered");
    } else {
      cl.add("filtered");
    }
  }
});
var $d_Ldotty_tools_scaladoc_DropdownHandler$package$ = new $TypeData().i($c_Ldotty_tools_scaladoc_DropdownHandler$package$, "dotty.tools.scaladoc.DropdownHandler$package$", ({
  d5: 1
}));
var $n_Ldotty_tools_scaladoc_DropdownHandler$package$;
function $m_Ldotty_tools_scaladoc_DropdownHandler$package$() {
  if ((!$n_Ldotty_tools_scaladoc_DropdownHandler$package$)) {
    $n_Ldotty_tools_scaladoc_DropdownHandler$package$ = new $c_Ldotty_tools_scaladoc_DropdownHandler$package$();
  }
  return $n_Ldotty_tools_scaladoc_DropdownHandler$package$;
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_InkuireJSSearchEngine() {
  this.lg = null;
  this.hY = null;
  this.lg = (pathToRoot + "scripts/");
  this.hY = new Worker((this.lg + "inkuire-worker.js"));
}
$p = $c_Ldotty_tools_scaladoc_InkuireJSSearchEngine.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_InkuireJSSearchEngine;
/** @constructor */
function $h_Ldotty_tools_scaladoc_InkuireJSSearchEngine() {
}
$h_Ldotty_tools_scaladoc_InkuireJSSearchEngine.prototype = $p;
$p.qx = (function(d) {
  return new $c_Ldotty_tools_scaladoc_InkuireMatch(d.prettifiedSignature, d.functionName, d.packageLocation, d.pageLocation, d.entryType, (d.mq | 0));
});
$p.rK = (function(s, callback, endCallback) {
  this.hY.onmessage = ((_$1$2) => (void 0));
  var func = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((msg$3) => {
    matchResult1: {
      var x1 = msg$3.data;
      if ((x1 === "engine_ready")) {
        break matchResult1;
      }
      if ((x1 === "new_query")) {
        break matchResult1;
      }
      if ((!(!x1.startsWith("query_ended")))) {
        endCallback.d($m_sc_StringOps$().o8(x1, 11));
        break matchResult1;
      }
      var matches = JSON.parse(x1).matches;
      var len = (matches.length | 0);
      var res = new Array(len);
      var i = 0;
      while ((i < len)) {
        res[i] = this.qx(matches[i]);
        i = ((1 + i) | 0);
      }
      $m_sjs_js_ArrayOps$().qP(res, callback);
    }
  }));
  this.hY.onmessage = $m_sjs_js_Any$().kM(func);
  this.hY.postMessage(s);
});
var $d_Ldotty_tools_scaladoc_InkuireJSSearchEngine = new $TypeData().i($c_Ldotty_tools_scaladoc_InkuireJSSearchEngine, "dotty.tools.scaladoc.InkuireJSSearchEngine", ({
  d6: 1
}));
function $s_Ldotty_tools_scaladoc_Main__main__AT__V(args) {
  try {
    $m_Ldotty_tools_scaladoc_Main$package$().pB();
  } catch (e) {
    if (false) {
      $m_s_util_CommandLineParser$().s4(e);
    } else {
      throw e;
    }
  }
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_Main$package$() {
}
$p = $c_Ldotty_tools_scaladoc_Main$package$.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_Main$package$;
/** @constructor */
function $h_Ldotty_tools_scaladoc_Main$package$() {
}
$h_Ldotty_tools_scaladoc_Main$package$.prototype = $p;
$p.pB = (function() {
  new $c_Ldotty_tools_scaladoc_Searchbar();
  new $c_Ldotty_tools_scaladoc_DropdownHandler();
  new $c_Ldotty_tools_scaladoc_TooltipNormalizer();
  return new $c_Ldotty_tools_scaladoc_CodeSnippets();
});
var $d_Ldotty_tools_scaladoc_Main$package$ = new $TypeData().i($c_Ldotty_tools_scaladoc_Main$package$, "dotty.tools.scaladoc.Main$package$", ({
  d7: 1
}));
var $n_Ldotty_tools_scaladoc_Main$package$;
function $m_Ldotty_tools_scaladoc_Main$package$() {
  if ((!$n_Ldotty_tools_scaladoc_Main$package$)) {
    $n_Ldotty_tools_scaladoc_Main$package$ = new $c_Ldotty_tools_scaladoc_Main$package$();
  }
  return $n_Ldotty_tools_scaladoc_Main$package$;
}
function $p_Ldotty_tools_scaladoc_PageSearchEngine__kindScoreBonus__T__I($thiz, kind) {
  switch (kind) {
    case "class": {
      return 5;
      break;
    }
    case "object":
    case "enum": {
      return 4;
      break;
    }
    case "trait": {
      return 3;
      break;
    }
    case "def":
    case "val":
    case "given":
    case "type": {
      return 1;
      break;
    }
    default: {
      return 0;
    }
  }
}
function $p_Ldotty_tools_scaladoc_PageSearchEngine__matchCompletnessBonus__I__I__I($thiz, nameCharacters, matchCharacters) {
  return ((((Math.imul(6, matchCharacters) / $checkIntDivisor(nameCharacters)) | 0) + ((nameCharacters === matchCharacters) ? 2 : 0)) | 0);
}
function $p_Ldotty_tools_scaladoc_PageSearchEngine__matchPages__Ldotty_tools_scaladoc_NameAndKindQuery__sci_List($thiz, query) {
  if ((query !== null)) {
    var x29 = query.gG;
    var x30 = query.gF;
    if (($m_s_None$() === x29)) {
      if (($m_s_None$() === x30)) {
        $m_sci_List$();
        return $m_sci_Nil$();
      }
      if ((x30 instanceof $c_s_Some)) {
        var kind = x30.cf;
        var this$6 = $p_Ldotty_tools_scaladoc_PageSearchEngine__filterKind__sci_List__T__sci_List($thiz, $thiz.fW, kind);
        var f = ((_$2$2) => new $c_Ldotty_tools_scaladoc_MatchResult(1, _$2$2, $m_sci_Set$EmptySet$()));
        if ((this$6 === $m_sci_Nil$())) {
          var $x_1 = $m_sci_Nil$();
        } else {
          var x0 = this$6.m();
          var h = new $c_sci_$colon$colon(f(x0), $m_sci_Nil$());
          var t = h;
          var rest = this$6.o();
          while ((rest !== $m_sci_Nil$())) {
            var x0$1 = rest.m();
            var nx = new $c_sci_$colon$colon(f(x0$1), $m_sci_Nil$());
            t.K = nx;
            t = nx;
            rest = rest.o();
          }
          var $x_1 = h;
        }
        return $x_1;
      }
    }
    if ((x29 instanceof $c_s_Some)) {
      var x22 = x29.cf;
      if ((x22 === "")) {
        if (x30.g()) {
          var $x_2 = $thiz.fW;
        } else {
          var x0$2 = x30.S();
          var $x_2 = $p_Ldotty_tools_scaladoc_PageSearchEngine__filterKind__sci_List__T__sci_List($thiz, $thiz.fW, x0$2);
        }
        var this$10 = $x_2;
        var f$1 = ((_$4$2) => new $c_Ldotty_tools_scaladoc_MatchResult(1, _$4$2, $m_sci_Set$EmptySet$()));
        if ((this$10 === $m_sci_Nil$())) {
          var $x_3 = $m_sci_Nil$();
        } else {
          var x0$3 = this$10.m();
          var h$1 = new $c_sci_$colon$colon(f$1(x0$3), $m_sci_Nil$());
          var t$1 = h$1;
          var rest$1 = this$10.o();
          while ((rest$1 !== $m_sci_Nil$())) {
            var x0$4 = rest$1.m();
            var nx$1 = new $c_sci_$colon$colon(f$1(x0$4), $m_sci_Nil$());
            t$1.K = nx$1;
            t$1 = nx$1;
            rest$1 = rest$1.o();
          }
          var $x_3 = h$1;
        }
        return $x_3;
      }
      if (x30.g()) {
        var kindFiltered = $thiz.fW;
      } else {
        var x0$5 = x30.S();
        var kindFiltered = $p_Ldotty_tools_scaladoc_PageSearchEngine__filterKind__sci_List__T__sci_List($thiz, $thiz.fW, x0$5);
      }
      var prematchedPages = $thiz.rH(kindFiltered, x22);
      if ((x22.length > 1)) {
        var f$2 = ((prematched$2) => {
          var finalMatch = $p_Ldotty_tools_scaladoc_PageSearchEngine__matchPage__Ldotty_tools_scaladoc_MatchResult__T__Ldotty_tools_scaladoc_MatchResult($thiz, prematched$2, x22);
          var bonusScore = (($p_Ldotty_tools_scaladoc_PageSearchEngine__kindScoreBonus__T__I($thiz, prematched$2.bW.dw) + $p_Ldotty_tools_scaladoc_PageSearchEngine__matchCompletnessBonus__I__I__I($thiz, prematched$2.bW.et.length, x22.length)) | 0);
          return new $c_Ldotty_tools_scaladoc_MatchResult(((finalMatch.cK + bonusScore) | 0), finalMatch.bW, finalMatch.dv);
        });
        if ((prematchedPages === $m_sci_Nil$())) {
          var $x_4 = $m_sci_Nil$();
        } else {
          var x0$6 = prematchedPages.m();
          var h$2 = new $c_sci_$colon$colon(f$2(x0$6), $m_sci_Nil$());
          var t$2 = h$2;
          var rest$2 = prematchedPages.o();
          while ((rest$2 !== $m_sci_Nil$())) {
            var x0$7 = rest$2.m();
            var nx$2 = new $c_sci_$colon$colon(f$2(x0$7), $m_sci_Nil$());
            t$2.K = nx$2;
            t$2 = nx$2;
            rest$2 = rest$2.o();
          }
          var $x_4 = h$2;
        }
        return $x_4;
      } else {
        return prematchedPages;
      }
    }
  }
  throw new $c_s_MatchError(query);
}
function $p_Ldotty_tools_scaladoc_PageSearchEngine__filterKind__sci_List__T__sci_List($thiz, pages, kind) {
  var f = ((_$6$2) => (_$6$2.dw === kind));
  var l$tailLocal1 = pages;
  while (true) {
    if (l$tailLocal1.g()) {
      return $m_sci_Nil$();
    } else {
      var h = l$tailLocal1.m();
      var t = l$tailLocal1.o();
      if ((!(!(!f(h))))) {
        l$tailLocal1 = t;
        continue;
      }
      var start = l$tailLocal1;
      var remaining$tailLocal1 = t;
      while (true) {
        if (remaining$tailLocal1.g()) {
          return start;
        } else {
          var x = remaining$tailLocal1.m();
          if ((!(!f(x)))) {
            remaining$tailLocal1 = remaining$tailLocal1.o();
            continue;
          }
          var firstMiss = remaining$tailLocal1;
          var newHead = new $c_sci_$colon$colon(start.m(), $m_sci_Nil$());
          var toProcess = start.o();
          var currentLast = newHead;
          while ((toProcess !== firstMiss)) {
            var newElem = new $c_sci_$colon$colon(toProcess.m(), $m_sci_Nil$());
            currentLast.K = newElem;
            currentLast = newElem;
            toProcess = toProcess.o();
          }
          var next = firstMiss.o();
          var nextToCopy = next;
          while ((!next.g())) {
            var head = next.m();
            if ((!(!f(head)))) {
              next = next.o();
            } else {
              while ((nextToCopy !== next)) {
                var newElem$2 = new $c_sci_$colon$colon(nextToCopy.m(), $m_sci_Nil$());
                currentLast.K = newElem$2;
                currentLast = newElem$2;
                nextToCopy = nextToCopy.o();
              }
              nextToCopy = next.o();
              next = next.o();
            }
          }
          if ((!nextToCopy.g())) {
            currentLast.K = nextToCopy;
          }
          return newHead;
        }
      }
    }
  }
}
function $p_Ldotty_tools_scaladoc_PageSearchEngine__prematchPage__Ldotty_tools_scaladoc_PageEntry__T__Ldotty_tools_scaladoc_MatchResult($thiz, page, search) {
  var pageName = page.et;
  var result = $p_Ldotty_tools_scaladoc_PageSearchEngine__prematchPageAcc$1__T__Ldotty_tools_scaladoc_PageEntry__T__I__I__sci_Set__I__I__Ldotty_tools_scaladoc_MatchResult($thiz, search, page, pageName, 0, 0, $m_sci_Set$EmptySet$(), 0, 0);
  return new $c_Ldotty_tools_scaladoc_MatchResult(((result.cK + $p_Ldotty_tools_scaladoc_PageSearchEngine__kindScoreBonus__T__I($thiz, page.dw)) | 0), result.bW, result.dv);
}
function $p_Ldotty_tools_scaladoc_PageSearchEngine__matchPage__Ldotty_tools_scaladoc_MatchResult__T__Ldotty_tools_scaladoc_MatchResult($thiz, prematched, nameSearch) {
  var this$4 = $m_Ldotty_tools_scaladoc_StringUtils$().kw(nameSearch);
  var f = ((_$9$2) => {
    var this$2 = $m_s_Predef$().ld(_$9$2);
    $m_sci_List$();
    return $m_sci_Nil$().cc(this$2);
  });
  if ((this$4 === $m_sci_Nil$())) {
    var $x_1 = $m_sci_Nil$();
  } else {
    var x0 = this$4.m();
    var h = new $c_sci_$colon$colon(f(x0), $m_sci_Nil$());
    var t = h;
    var rest = this$4.o();
    while ((rest !== $m_sci_Nil$())) {
      var x0$1 = rest.m();
      var nx = new $c_sci_$colon$colon(f(x0$1), $m_sci_Nil$());
      t.K = nx;
      t = nx;
      rest = rest.o();
    }
    var $x_1 = h;
  }
  var searchTokens = $x_1;
  var this$8 = prematched.bW.fV;
  var f$1 = ((_$10$2) => {
    var this$6 = $m_s_Predef$().ld(_$10$2);
    $m_sci_List$();
    return $m_sci_Nil$().cc(this$6);
  });
  if ((this$8 === $m_sci_Nil$())) {
    var $x_2 = $m_sci_Nil$();
  } else {
    var x0$2 = this$8.m();
    var h$1 = new $c_sci_$colon$colon(f$1(x0$2), $m_sci_Nil$());
    var t$1 = h$1;
    var rest$1 = this$8.o();
    while ((rest$1 !== $m_sci_Nil$())) {
      var x0$3 = rest$1.m();
      var nx$1 = new $c_sci_$colon$colon(f$1(x0$3), $m_sci_Nil$());
      t$1.K = nx$1;
      t$1 = nx$1;
      rest$1 = rest$1.o();
    }
    var $x_2 = h$1;
  }
  var pageTokens = $x_2;
  var pageName = prematched.bW.et;
  var searchTokensLifted = new $c_s_PartialFunction$Lifted(searchTokens);
  var pageTokensLifted = new $c_s_PartialFunction$Lifted(pageTokens);
  var matchedTokens = $p_Ldotty_tools_scaladoc_PageSearchEngine__matchTokens$1__F1__F1__I__I__sci_Set__sci_Set($thiz, searchTokensLifted, pageTokensLifted, 0, 0, $m_sci_Set$EmptySet$());
  var f$2 = ((_$13$2) => _$13$2.l());
  if ((searchTokens === $m_sci_Nil$())) {
    var $x_3 = $m_sci_Nil$();
  } else {
    var x0$4 = searchTokens.m();
    var h$2 = new $c_sci_$colon$colon(f$2(x0$4), $m_sci_Nil$());
    var t$2 = h$2;
    var rest$2 = searchTokens.o();
    while ((rest$2 !== $m_sci_Nil$())) {
      var x0$5 = rest$2.m();
      var nx$2 = new $c_sci_$colon$colon(f$2(x0$5), $m_sci_Nil$());
      t$2.K = nx$2;
      t$2 = nx$2;
      rest$2 = rest$2.o();
    }
    var $x_3 = h$2;
  }
  var this$11 = $x_3;
  $m_sci_List$();
  var b = new $c_scm_ListBuffer();
  $f_scm_Builder__sizeHint__sc_IterableOnce__I__V(b, this$11, 0);
  var acc = 0;
  b.gm(acc);
  var it = this$11.c();
  while (it.k()) {
    var x0$6 = acc;
    var x1 = it.f();
    acc = (((x0$6 | 0) + (x1 | 0)) | 0);
    b.gm(acc);
  }
  var searchTokenPositions = b.c2();
  var f$3 = ((_$16$2) => _$16$2.l());
  if ((pageTokens === $m_sci_Nil$())) {
    var $x_4 = $m_sci_Nil$();
  } else {
    var x0$7 = pageTokens.m();
    var h$3 = new $c_sci_$colon$colon(f$3(x0$7), $m_sci_Nil$());
    var t$3 = h$3;
    var rest$3 = pageTokens.o();
    while ((rest$3 !== $m_sci_Nil$())) {
      var x0$8 = rest$3.m();
      var nx$3 = new $c_sci_$colon$colon(f$3(x0$8), $m_sci_Nil$());
      t$3.K = nx$3;
      t$3 = nx$3;
      rest$3 = rest$3.o();
    }
    var $x_4 = h$3;
  }
  var this$14 = $x_4;
  $m_sci_List$();
  var b$1 = new $c_scm_ListBuffer();
  $f_scm_Builder__sizeHint__sc_IterableOnce__I__V(b$1, this$14, 0);
  var acc$1 = 0;
  b$1.gm(acc$1);
  var it$1 = this$14.c();
  while (it$1.k()) {
    var x0$9 = acc$1;
    var x1$1 = it$1.f();
    acc$1 = (((x0$9 | 0) + (x1$1 | 0)) | 0);
    b$1.gm(acc$1);
  }
  var pageTokensPositions = b$1.c2();
  var highScoreMatch = $p_Ldotty_tools_scaladoc_PageSearchEngine__findHighScoreMatch$1__T__Ldotty_tools_scaladoc_MatchResult__T__sci_List__sci_Set__sci_List__I__I__I__I__sci_Set__I__I__s_Option($thiz, nameSearch, prematched, pageName, searchTokenPositions, matchedTokens, pageTokensPositions, 0, 0, 0, 0, $m_sci_Set$EmptySet$(), 0, 0);
  return (highScoreMatch.g() ? prematched : highScoreMatch.S());
}
function $p_Ldotty_tools_scaladoc_PageSearchEngine__prematchPageAcc$1__T__Ldotty_tools_scaladoc_PageEntry__T__I__I__sci_Set__I__I__Ldotty_tools_scaladoc_MatchResult($thiz, search$2, page$1, pageName$1, nameIndex, searchIndex, acc, scoreAcc, consecutiveMatches) {
  var consecutiveMatches$tailLocal1 = consecutiveMatches;
  var scoreAcc$tailLocal1 = scoreAcc;
  var acc$tailLocal1 = acc;
  var searchIndex$tailLocal1 = searchIndex;
  var nameIndex$tailLocal1 = nameIndex;
  while (true) {
    if ((searchIndex$tailLocal1 >= search$2.length)) {
      return new $c_Ldotty_tools_scaladoc_MatchResult(scoreAcc$tailLocal1, page$1, acc$tailLocal1);
    } else if ((nameIndex$tailLocal1 >= pageName$1.length)) {
      return new $c_Ldotty_tools_scaladoc_MatchResult(0, page$1, $m_sci_Set$EmptySet$());
    } else if ((($m_sc_StringOps$(), $m_jl_Character$().ja(pageName$1.charCodeAt(nameIndex$tailLocal1))) === ($m_sc_StringOps$(), $m_jl_Character$().ja(search$2.charCodeAt(searchIndex$tailLocal1))))) {
      var score$1 = ((((consecutiveMatches$tailLocal1 > 0) | 0) + $thiz.jd.f1(nameIndex$tailLocal1)) | 0);
      var nameIndex$tailLocal1$tmp1 = ((1 + nameIndex$tailLocal1) | 0);
      var searchIndex$tailLocal1$tmp1 = ((1 + searchIndex$tailLocal1) | 0);
      var acc$tailLocal1$tmp1 = acc$tailLocal1.dT(nameIndex$tailLocal1);
      var scoreAcc$tailLocal1$tmp1 = ((scoreAcc$tailLocal1 + score$1) | 0);
      var consecutiveMatches$tailLocal1$tmp1 = ((1 + consecutiveMatches$tailLocal1) | 0);
      nameIndex$tailLocal1 = nameIndex$tailLocal1$tmp1;
      searchIndex$tailLocal1 = searchIndex$tailLocal1$tmp1;
      acc$tailLocal1 = acc$tailLocal1$tmp1;
      scoreAcc$tailLocal1 = scoreAcc$tailLocal1$tmp1;
      consecutiveMatches$tailLocal1 = consecutiveMatches$tailLocal1$tmp1;
    } else {
      var nameIndex$tailLocal1$tmp2 = ((1 + nameIndex$tailLocal1) | 0);
      nameIndex$tailLocal1 = nameIndex$tailLocal1$tmp2;
      consecutiveMatches$tailLocal1 = 0;
    }
  }
}
function $p_Ldotty_tools_scaladoc_PageSearchEngine__matchTokens$1__F1__F1__I__I__sci_Set__sci_Set($thiz, searchTokensLifted$1, pageTokensLifted$1, searchTokenIndex, pageTokenIndex, acc) {
  var acc$tailLocal2 = acc;
  var pageTokenIndex$tailLocal1 = pageTokenIndex;
  var searchTokenIndex$tailLocal1 = searchTokenIndex;
  while (true) {
    var this$1 = searchTokensLifted$1.d(searchTokenIndex$tailLocal1);
    var _1 = (this$1.g() ? $m_s_None$() : new $c_s_Some(this$1.S()));
    var this$3 = pageTokensLifted$1.d(pageTokenIndex$tailLocal1);
    var _2 = (this$3.g() ? $m_s_None$() : new $c_s_Some(this$3.S()));
    matchAlts5: {
      matchAlts6: {
        if (($m_s_None$() === _1)) {
          break matchAlts6;
        }
        if (($m_s_None$() === _2)) {
          break matchAlts6;
        }
        break matchAlts5;
      }
      return acc$tailLocal2;
    }
    if ((_1 instanceof $c_s_Some)) {
      var x58 = _1.cf;
      if ((x58 instanceof $c_sci_$colon$colon)) {
        var searchHead = $uC(x58.gb);
        if ((_2 instanceof $c_s_Some)) {
          var x53 = _2.cf;
          if ((x53 instanceof $c_sci_$colon$colon)) {
            if ((searchHead === $uC(x53.gb))) {
              var searchTokenIndex$tailLocal1$tmp1 = ((1 + searchTokenIndex$tailLocal1) | 0);
              var pageTokenIndex$tailLocal1$tmp1 = ((1 + pageTokenIndex$tailLocal1) | 0);
              var acc$tailLocal2$tmp1 = acc$tailLocal2.dT(new $c_T2$mcII$sp(searchTokenIndex$tailLocal1, pageTokenIndex$tailLocal1));
              searchTokenIndex$tailLocal1 = searchTokenIndex$tailLocal1$tmp1;
              pageTokenIndex$tailLocal1 = pageTokenIndex$tailLocal1$tmp1;
              acc$tailLocal2 = acc$tailLocal2$tmp1;
              continue;
            } else {
              pageTokenIndex$tailLocal1 = ((1 + pageTokenIndex$tailLocal1) | 0);
              continue;
            }
          }
        }
      }
      if (((_2 instanceof $c_s_Some) && (_2.cf instanceof $c_sci_$colon$colon))) {
        searchTokenIndex$tailLocal1 = ((1 + searchTokenIndex$tailLocal1) | 0);
        continue;
      }
      if ((x58 instanceof $c_sci_$colon$colon)) {
        if ((_2 instanceof $c_s_Some)) {
          pageTokenIndex$tailLocal1 = ((1 + pageTokenIndex$tailLocal1) | 0);
          continue;
        }
      }
    }
    var searchTokenIndex$tailLocal1$tmp2 = ((1 + searchTokenIndex$tailLocal1) | 0);
    var pageTokenIndex$tailLocal1$tmp2 = ((1 + pageTokenIndex$tailLocal1) | 0);
    searchTokenIndex$tailLocal1 = searchTokenIndex$tailLocal1$tmp2;
    pageTokenIndex$tailLocal1 = pageTokenIndex$tailLocal1$tmp2;
  }
}
function $p_Ldotty_tools_scaladoc_PageSearchEngine__recalculateTokenIndex$1__sci_Seq__I__I__I($thiz, tokenPositions, previousIndex, position) {
  return (((tokenPositions.l() <= ((1 + previousIndex) | 0)) || ((tokenPositions.r(((1 + previousIndex) | 0)) | 0) > position)) ? previousIndex : ((1 + previousIndex) | 0));
}
function $p_Ldotty_tools_scaladoc_PageSearchEngine__getMatchScore$1__I__I__I__I($thiz, consecutiveMatches$tailLocal2$1, matchedPagePosition, matchedPageTokenStart) {
  var consecutiveMatchesScore = ((consecutiveMatches$tailLocal2$1 > 0) | 0);
  var matchPositionScore = $thiz.jd.f1(((matchedPagePosition - matchedPageTokenStart) | 0));
  return ((((consecutiveMatchesScore + matchPositionScore) | 0) + (((matchPositionScore > 0) && (matchedPageTokenStart === 0)) ? 3 : 0)) | 0);
}
function $p_Ldotty_tools_scaladoc_PageSearchEngine__findHighScoreMatch$1__T__Ldotty_tools_scaladoc_MatchResult__T__sci_List__sci_Set__sci_List__I__I__I__I__sci_Set__I__I__s_Option($thiz, nameSearch$2, prematched$1, pageName$2, searchTokenPositions$1, matchedTokens$1, pageTokensPositions$1, searchTokenIndex, searchPosition, pageTokenIndex, pagePosition, positionAcc, scoreAcc, consecutiveMatches) {
  var consecutiveMatches$tailLocal2 = consecutiveMatches;
  var scoreAcc$tailLocal2 = scoreAcc;
  var positionAcc$tailLocal1 = positionAcc;
  var pagePosition$tailLocal1 = pagePosition;
  var pageTokenIndex$tailLocal2 = pageTokenIndex;
  var searchPosition$tailLocal1 = searchPosition;
  var searchTokenIndex$tailLocal2 = searchTokenIndex;
  while (true) {
    if ((searchPosition$tailLocal1 >= nameSearch$2.length)) {
      return new $c_s_Some(new $c_Ldotty_tools_scaladoc_MatchResult(scoreAcc$tailLocal2, prematched$1.bW, positionAcc$tailLocal1));
    } else if ((pagePosition$tailLocal1 >= pageName$2.length)) {
      return $m_s_None$();
    } else {
      var currentSearchTokenStart = ($f_sc_LinearSeqOps__apply__I__O(searchTokenPositions$1, searchTokenIndex$tailLocal2) | 0);
      var this$4 = matchedTokens$1.hG(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((searchTokenIndex$tailLocal2$1) => ((_$19$2) => (_$19$2.nE() === searchTokenIndex$tailLocal2$1)))(searchTokenIndex$tailLocal2)));
      var matchingPageToken = (this$4.g() ? $m_s_None$() : new $c_s_Some(this$4.S().kk()));
      var searchChar = $m_jl_Character$().ja(nameSearch$2.charCodeAt(searchPosition$tailLocal1));
      var pageChar = $m_jl_Character$().ja(pageName$2.charCodeAt(pagePosition$tailLocal1));
      if ((matchingPageToken instanceof $c_s_Some)) {
        var matchingToken = (matchingPageToken.cf | 0);
        if ((searchPosition$tailLocal1 === currentSearchTokenStart)) {
          var matchedTokenPosition = ($f_sc_LinearSeqOps__apply__I__O(pageTokensPositions$1, matchingToken) | 0);
          var searchTokenIndex$tailLocal2$tmp1 = $p_Ldotty_tools_scaladoc_PageSearchEngine__recalculateTokenIndex$1__sci_Seq__I__I__I($thiz, searchTokenPositions$1, searchTokenIndex$tailLocal2, ((1 + searchPosition$tailLocal1) | 0));
          var searchPosition$tailLocal1$tmp1 = ((1 + searchPosition$tailLocal1) | 0);
          var pageTokenIndex$tailLocal2$tmp1 = $p_Ldotty_tools_scaladoc_PageSearchEngine__recalculateTokenIndex$1__sci_Seq__I__I__I($thiz, pageTokensPositions$1, pageTokenIndex$tailLocal2, ((1 + matchedTokenPosition) | 0));
          var pagePosition$tailLocal1$tmp1 = ((1 + matchedTokenPosition) | 0);
          var positionAcc$tailLocal1$tmp1 = positionAcc$tailLocal1.dT(matchedTokenPosition);
          var scoreAcc$tailLocal2$tmp1 = ((scoreAcc$tailLocal2 + $p_Ldotty_tools_scaladoc_PageSearchEngine__getMatchScore$1__I__I__I__I($thiz, consecutiveMatches$tailLocal2, matchedTokenPosition, matchedTokenPosition)) | 0);
          var consecutiveMatches$tailLocal2$tmp1 = ((1 + consecutiveMatches$tailLocal2) | 0);
          searchTokenIndex$tailLocal2 = searchTokenIndex$tailLocal2$tmp1;
          searchPosition$tailLocal1 = searchPosition$tailLocal1$tmp1;
          pageTokenIndex$tailLocal2 = pageTokenIndex$tailLocal2$tmp1;
          pagePosition$tailLocal1 = pagePosition$tailLocal1$tmp1;
          positionAcc$tailLocal1 = positionAcc$tailLocal1$tmp1;
          scoreAcc$tailLocal2 = scoreAcc$tailLocal2$tmp1;
          consecutiveMatches$tailLocal2 = consecutiveMatches$tailLocal2$tmp1;
          continue;
        }
      }
      if ((searchChar === pageChar)) {
        var this$12 = (matchingPageToken.g() ? $m_s_None$() : new $c_s_Some($f_sc_LinearSeqOps__apply__I__O(pageTokensPositions$1, (matchingPageToken.S() | 0))));
        var matchedTokenPosition$2 = ((this$12.g() ? 0 : this$12.S()) | 0);
        var searchTokenIndex$tailLocal2$tmp2 = $p_Ldotty_tools_scaladoc_PageSearchEngine__recalculateTokenIndex$1__sci_Seq__I__I__I($thiz, searchTokenPositions$1, searchTokenIndex$tailLocal2, ((1 + searchPosition$tailLocal1) | 0));
        var searchPosition$tailLocal1$tmp2 = ((1 + searchPosition$tailLocal1) | 0);
        var pageTokenIndex$tailLocal2$tmp2 = $p_Ldotty_tools_scaladoc_PageSearchEngine__recalculateTokenIndex$1__sci_Seq__I__I__I($thiz, pageTokensPositions$1, pageTokenIndex$tailLocal2, ((1 + pagePosition$tailLocal1) | 0));
        var pagePosition$tailLocal1$tmp2 = ((1 + pagePosition$tailLocal1) | 0);
        var positionAcc$tailLocal1$tmp2 = positionAcc$tailLocal1.dT(pagePosition$tailLocal1);
        var scoreAcc$tailLocal2$tmp2 = ((scoreAcc$tailLocal2 + $p_Ldotty_tools_scaladoc_PageSearchEngine__getMatchScore$1__I__I__I__I($thiz, consecutiveMatches$tailLocal2, pagePosition$tailLocal1, matchedTokenPosition$2)) | 0);
        var consecutiveMatches$tailLocal2$tmp2 = ((1 + consecutiveMatches$tailLocal2) | 0);
        searchTokenIndex$tailLocal2 = searchTokenIndex$tailLocal2$tmp2;
        searchPosition$tailLocal1 = searchPosition$tailLocal1$tmp2;
        pageTokenIndex$tailLocal2 = pageTokenIndex$tailLocal2$tmp2;
        pagePosition$tailLocal1 = pagePosition$tailLocal1$tmp2;
        positionAcc$tailLocal1 = positionAcc$tailLocal1$tmp2;
        scoreAcc$tailLocal2 = scoreAcc$tailLocal2$tmp2;
        consecutiveMatches$tailLocal2 = consecutiveMatches$tailLocal2$tmp2;
        continue;
      }
      var pageTokenIndex$tailLocal2$tmp3 = $p_Ldotty_tools_scaladoc_PageSearchEngine__recalculateTokenIndex$1__sci_Seq__I__I__I($thiz, pageTokensPositions$1, pageTokenIndex$tailLocal2, ((1 + pagePosition$tailLocal1) | 0));
      var pagePosition$tailLocal1$tmp3 = ((1 + pagePosition$tailLocal1) | 0);
      pageTokenIndex$tailLocal2 = pageTokenIndex$tailLocal2$tmp3;
      pagePosition$tailLocal1 = pagePosition$tailLocal1$tmp3;
      consecutiveMatches$tailLocal2 = 0;
    }
  }
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_PageSearchEngine(pages) {
  this.fW = null;
  this.jd = null;
  this.fW = pages;
  this.jd = new $c_s_PartialFunction$OrElse(new $c_sci_$colon$colon(8, new $c_sci_$colon$colon(4, new $c_sci_$colon$colon(2, new $c_sci_$colon$colon(1, $m_sci_Nil$())))), ($m_s_PartialFunction$(), new $c_s_PartialFunction$$anon$2(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$1$3) => 0)))));
}
$p = $c_Ldotty_tools_scaladoc_PageSearchEngine.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_PageSearchEngine;
/** @constructor */
function $h_Ldotty_tools_scaladoc_PageSearchEngine() {
}
$h_Ldotty_tools_scaladoc_PageSearchEngine.prototype = $p;
$p.rJ = (function(query) {
  var this$3 = $p_Ldotty_tools_scaladoc_PageSearchEngine__matchPages__Ldotty_tools_scaladoc_NameAndKindQuery__sci_List(this, query);
  var f = ((x$1$2) => {
    if ((x$1$2 !== null)) {
      return (x$1$2.cK >= 0);
    }
    throw new $c_s_MatchError(x$1$2);
  });
  _return: {
    var $x_1;
    var l$tailLocal1 = this$3;
    while (true) {
      if (l$tailLocal1.g()) {
        var $x_1 = $m_sci_Nil$();
        break;
      } else {
        var h = l$tailLocal1.m();
        var t = l$tailLocal1.o();
        if ((!(!(!f(h))))) {
          l$tailLocal1 = t;
          continue;
        }
        var start = l$tailLocal1;
        var remaining$tailLocal1 = t;
        while (true) {
          if (remaining$tailLocal1.g()) {
            var $x_1 = start;
            break _return;
          } else {
            var x = remaining$tailLocal1.m();
            if ((!(!f(x)))) {
              remaining$tailLocal1 = remaining$tailLocal1.o();
              continue;
            }
            var firstMiss = remaining$tailLocal1;
            var newHead = new $c_sci_$colon$colon(start.m(), $m_sci_Nil$());
            var toProcess = start.o();
            var currentLast = newHead;
            while ((toProcess !== firstMiss)) {
              var newElem = new $c_sci_$colon$colon(toProcess.m(), $m_sci_Nil$());
              currentLast.K = newElem;
              currentLast = newElem;
              toProcess = toProcess.o();
            }
            var next = firstMiss.o();
            var nextToCopy = next;
            while ((!next.g())) {
              var head = next.m();
              if ((!(!f(head)))) {
                next = next.o();
              } else {
                while ((nextToCopy !== next)) {
                  var newElem$2 = new $c_sci_$colon$colon(nextToCopy.m(), $m_sci_Nil$());
                  currentLast.K = newElem$2;
                  currentLast = newElem$2;
                  nextToCopy = nextToCopy.o();
                }
                nextToCopy = next.o();
                next = next.o();
              }
            }
            if ((!nextToCopy.g())) {
              currentLast.K = nextToCopy;
            }
            var $x_1 = newHead;
            break _return;
          }
        }
      }
    }
  }
  return $f_sc_SeqOps__sortBy__F1__s_math_Ordering__O($x_1, new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$2$1) => {
    if ((x$1$2$1 !== null)) {
      return ((-x$1$2$1.cK) | 0);
    }
    throw new $c_s_MatchError(x$1$2$1);
  })), $m_s_math_Ordering$Int$());
});
$p.rH = (function(pages, search) {
  var f = ((_$7$2) => $p_Ldotty_tools_scaladoc_PageSearchEngine__prematchPage__Ldotty_tools_scaladoc_PageEntry__T__Ldotty_tools_scaladoc_MatchResult(this, _$7$2, search));
  if ((pages === $m_sci_Nil$())) {
    var $x_1 = $m_sci_Nil$();
  } else {
    var x0 = pages.m();
    var h = new $c_sci_$colon$colon(f(x0), $m_sci_Nil$());
    var t = h;
    var rest = pages.o();
    while ((rest !== $m_sci_Nil$())) {
      var x0$1 = rest.m();
      var nx = new $c_sci_$colon$colon(f(x0$1), $m_sci_Nil$());
      t.K = nx;
      t = nx;
      rest = rest.o();
    }
    var $x_1 = h;
  }
  var this$4 = $x_1;
  var f$1 = ((_$8$2) => (!_$8$2.dv.g()));
  var l$tailLocal1 = this$4;
  while (true) {
    if (l$tailLocal1.g()) {
      return $m_sci_Nil$();
    } else {
      var h$1 = l$tailLocal1.m();
      var t$1 = l$tailLocal1.o();
      if ((!(!(!f$1(h$1))))) {
        l$tailLocal1 = t$1;
        continue;
      }
      var start = l$tailLocal1;
      var remaining$tailLocal1 = t$1;
      while (true) {
        if (remaining$tailLocal1.g()) {
          return start;
        } else {
          var x = remaining$tailLocal1.m();
          if ((!(!f$1(x)))) {
            remaining$tailLocal1 = remaining$tailLocal1.o();
            continue;
          }
          var firstMiss = remaining$tailLocal1;
          var newHead = new $c_sci_$colon$colon(start.m(), $m_sci_Nil$());
          var toProcess = start.o();
          var currentLast = newHead;
          while ((toProcess !== firstMiss)) {
            var newElem = new $c_sci_$colon$colon(toProcess.m(), $m_sci_Nil$());
            currentLast.K = newElem;
            currentLast = newElem;
            toProcess = toProcess.o();
          }
          var next = firstMiss.o();
          var nextToCopy = next;
          while ((!next.g())) {
            var head = next.m();
            if ((!(!f$1(head)))) {
              next = next.o();
            } else {
              while ((nextToCopy !== next)) {
                var newElem$2 = new $c_sci_$colon$colon(nextToCopy.m(), $m_sci_Nil$());
                currentLast.K = newElem$2;
                currentLast = newElem$2;
                nextToCopy = nextToCopy.o();
              }
              nextToCopy = next.o();
              next = next.o();
            }
          }
          if ((!nextToCopy.g())) {
            currentLast.K = nextToCopy;
          }
          return newHead;
        }
      }
    }
  }
});
var $d_Ldotty_tools_scaladoc_PageSearchEngine = new $TypeData().i($c_Ldotty_tools_scaladoc_PageSearchEngine, "dotty.tools.scaladoc.PageSearchEngine", ({
  d9: 1
}));
/** @constructor */
function $c_Ldotty_tools_scaladoc_QueryParser() {
  this.lj = null;
  this.li = null;
  this.lk = null;
  this.lh = null;
  this.ll = null;
  this.lj = $m_sci_Seq$().nT(new $c_sjsr_WrappedVarArgs(["class", "trait", "enum", "object", "def", "val", "var", "package", "given", "type"]));
  this.li = $m_sc_StringOps$().j3((("(?i)" + $f_sc_IterableOnceOps__mkString__T__T__T__T(this.lj, "(", "|", ")")) + " (.*)"));
  this.lk = $m_sc_StringOps$().j3("(.*)");
  this.lh = $m_sc_StringOps$().j3("`(.*)`");
  this.ll = $m_sc_StringOps$().j3("(.*=>.*)");
}
$p = $c_Ldotty_tools_scaladoc_QueryParser.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_QueryParser;
/** @constructor */
function $h_Ldotty_tools_scaladoc_QueryParser() {
}
$h_Ldotty_tools_scaladoc_QueryParser.prototype = $p;
$p.rG = (function(query) {
  if ((query !== null)) {
    var x9 = this.lh.jc(query);
    if ((!x9.g())) {
      var x10 = x9.S();
      if ((x10.aI(1) === 0)) {
        return new $c_Ldotty_tools_scaladoc_NameAndKindQuery(new $c_s_Some($f_sc_LinearSeqOps__apply__I__O(x10, 0)), $m_s_None$());
      }
    }
    var x5 = this.li.jc(query);
    if ((!x5.g())) {
      var x6 = x5.S();
      if ((x6.aI(2) === 0)) {
        var x7 = $f_sc_LinearSeqOps__apply__I__O(x6, 0);
        return new $c_Ldotty_tools_scaladoc_NameAndKindQuery(new $c_s_Some($f_sc_LinearSeqOps__apply__I__O(x6, 1)), new $c_s_Some(x7));
      }
    }
    var x2 = this.lk.jc(query);
    if ((!x2.g())) {
      var x3 = x2.S();
      if ((x3.aI(1) === 0)) {
        return new $c_Ldotty_tools_scaladoc_NameAndKindQuery(new $c_s_Some($f_sc_LinearSeqOps__apply__I__O(x3, 0)), $m_s_None$());
      }
    }
  }
  return new $c_Ldotty_tools_scaladoc_NameAndKindQuery($m_s_None$(), $m_s_None$());
});
$p.rF = (function(query) {
  if ((query !== null)) {
    var x13 = this.ll.jc(query);
    if ((!x13.g())) {
      var x14 = x13.S();
      if ((x14.aI(1) === 0)) {
        return new $c_Ldotty_tools_scaladoc_SignatureQuery($f_sc_LinearSeqOps__apply__I__O(x14, 0));
      }
    }
  }
  return this.rG(query);
});
var $d_Ldotty_tools_scaladoc_QueryParser = new $TypeData().i($c_Ldotty_tools_scaladoc_QueryParser, "dotty.tools.scaladoc.QueryParser", ({
  da: 1
}));
function $ct_Ldotty_tools_scaladoc_SafeLocalStorage__T__sjs_js_Any__($thiz, key, defaultValue) {
  $thiz.i0 = key;
  $thiz.fX = defaultValue;
  try {
    window.localStorage.setItem("__TEST__KEY__", "");
    window.localStorage.removeItem("__TEST__KEY__");
    var $x_1 = true;
  } catch (e) {
    var $x_1 = false;
  }
  $thiz.hZ = $x_1;
  return $thiz;
}
function $p_Ldotty_tools_scaladoc_SafeLocalStorage__parseData__T__sjs_js_Any($thiz, data) {
  try {
    var this$1 = $m_s_Option$().hB(JSON.parse(data));
    return (this$1.g() ? $thiz.fX : this$1.S());
  } catch (e) {
    return $thiz.fX;
  }
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_SafeLocalStorage() {
  this.i0 = null;
  this.fX = null;
  this.hZ = false;
}
$p = $c_Ldotty_tools_scaladoc_SafeLocalStorage.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_SafeLocalStorage;
/** @constructor */
function $h_Ldotty_tools_scaladoc_SafeLocalStorage() {
}
$h_Ldotty_tools_scaladoc_SafeLocalStorage.prototype = $p;
$p.iW = (function() {
  var defaultValue = this.fX;
  return (this.hZ ? $p_Ldotty_tools_scaladoc_SafeLocalStorage__parseData__T__sjs_js_Any(this, window.localStorage.getItem(this.i0)) : defaultValue);
});
$p.s1 = (function(data) {
  if (this.hZ) {
    var a = JSON.stringify(data);
    window.localStorage.setItem(this.i0, a);
  }
});
$p.g = (function() {
  return $m_sr_BoxesRunTime$().n(this.iW(), this.fX);
});
/** @constructor */
function $c_Ldotty_tools_scaladoc_Searchbar() {
  this.lo = null;
  this.lp = null;
  this.lq = null;
  this.ln = null;
  var this$2 = $ct_sjs_js_WrappedArray__sjs_js_Array__(new $c_sjs_js_WrappedArray(), pages);
  $m_sci_List$();
  var this$5 = $m_sci_Nil$().cc(this$2);
  var f = ((jsObj$2) => $m_Ldotty_tools_scaladoc_PageEntry$().q0(jsObj$2));
  if ((this$5 === $m_sci_Nil$())) {
    var $x_1 = $m_sci_Nil$();
  } else {
    var x0 = this$5.m();
    var h = new $c_sci_$colon$colon(f(x0), $m_sci_Nil$());
    var t = h;
    var rest = this$5.o();
    while ((rest !== $m_sci_Nil$())) {
      var x0$1 = rest.m();
      var nx = new $c_sci_$colon$colon(f(x0$1), $m_sci_Nil$());
      t.K = nx;
      t = nx;
      rest = rest.o();
    }
    var $x_1 = h;
  }
  this.lo = $x_1;
  this.lp = new $c_Ldotty_tools_scaladoc_QueryParser();
  this.lq = new $c_Ldotty_tools_scaladoc_PageSearchEngine(this.lo);
  this.ln = new $c_Ldotty_tools_scaladoc_InkuireJSSearchEngine();
  new $c_Ldotty_tools_scaladoc_SearchbarComponent(this.lq, this.ln, this.lp);
}
$p = $c_Ldotty_tools_scaladoc_Searchbar.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_Searchbar;
/** @constructor */
function $h_Ldotty_tools_scaladoc_Searchbar() {
}
$h_Ldotty_tools_scaladoc_Searchbar.prototype = $p;
var $d_Ldotty_tools_scaladoc_Searchbar = new $TypeData().i($c_Ldotty_tools_scaladoc_Searchbar, "dotty.tools.scaladoc.Searchbar", ({
  dd: 1
}));
function $p_Ldotty_tools_scaladoc_SearchbarComponent__generateRootDiv__Lorg_scalajs_dom_HTMLDivElement($thiz) {
  var cancelButton = $m_Ldotty_tools_scaladoc_utils_HTML$().b0.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "scaladoc-searchbar-cancel-button body-small")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Cancel"]))));
  cancelButton.onclick = ((event$2) => document.body.removeChild($thiz.bX));
  var inputContainer = $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "scaladoc-searchbar-input-container")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$thiz.cs, cancelButton]))));
  var element = $m_s_util_ChainingOps$().f4($m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().eu.v, "scaladoc-searchbar")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([inputContainer, $thiz.ag])))), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((elem$3) => {
    elem$3.addEventListener("mousedown", ((e$2) => ((e$2.target.id !== "scaladoc-searchbar") ? (e$2.stopPropagation(), (void 0)) : (void 0))));
    elem$3.addEventListener("keydown", ((x$1$2) => {
      matchResult9: {
        if ((!(!(x$1$2 instanceof KeyboardEvent)))) {
          if (((x$1$2.keyCode | 0) === 40)) {
            $p_Ldotty_tools_scaladoc_SearchbarComponent__handleArrowDown__V($thiz);
            break matchResult9;
          } else if (((x$1$2.keyCode | 0) === 38)) {
            $p_Ldotty_tools_scaladoc_SearchbarComponent__handleArrowUp__V($thiz);
            break matchResult9;
          } else if (((x$1$2.keyCode | 0) === 13)) {
            $p_Ldotty_tools_scaladoc_SearchbarComponent__handleEnter__V($thiz);
            break matchResult9;
          } else if (((x$1$2.keyCode | 0) === 27)) {
            $p_Ldotty_tools_scaladoc_SearchbarComponent__handleEscape__Lorg_scalajs_dom_Node($thiz);
            break matchResult9;
          } else {
            break matchResult9;
          }
        }
        throw new $c_s_MatchError(x$1$2);
      }
    }));
  })));
  var searchbarFooter = $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().eu.v, "searchbar-footer"), $ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "body-small")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().b0.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "searchbar-footer-left-container")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Smart search:"])))), $m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["CC "])))), "to find CamelCase phrases"])))), $m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["A=>B "])))), "to find CamelCase signatures"]))))])))), $m_Ldotty_tools_scaladoc_utils_HTML$().b0.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "searchbar-footer-right-container")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Esc "])))), "to close"])))), $m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Arrows "])))), "to navigate"])))), $m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Enter "])))), "to select"]))))]))))]))));
  var rootParent = $m_s_util_ChainingOps$().f4($m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().eu.v, "searchbar-container")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([element, searchbarFooter])))), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((rootElem$3) => {
    rootElem$3.addEventListener("mousedown", ((e$2$1) => $p_Ldotty_tools_scaladoc_SearchbarComponent__handleEscape__Lorg_scalajs_dom_Node($thiz)));
  })));
  $thiz.lv.addEventListener("mousedown", ((e$2$2) => {
    e$2$2.stopPropagation();
  }));
  var \u03b43$ = document.body;
  \u03b43$.addEventListener("mousedown", ((e$2$3) => ((!(!document.body.contains(element))) ? ($p_Ldotty_tools_scaladoc_SearchbarComponent__handleEscape__Lorg_scalajs_dom_Node($thiz), (void 0)) : (void 0))));
  return rootParent;
}
function $p_Ldotty_tools_scaladoc_SearchbarComponent__handleArrowUp__V($thiz) {
  var selectedElement = $thiz.ag.querySelector("[selected]");
  if ((selectedElement !== null)) {
    selectedElement.removeAttribute("selected");
    var sibling = $p_Ldotty_tools_scaladoc_SearchbarComponent__recur$1__Lorg_scalajs_dom_Element__Lorg_scalajs_dom_Element($thiz, selectedElement);
    if ((sibling !== null)) {
      sibling.setAttribute("selected", "");
      $thiz.ag.scrollTop = ((+sibling.offsetTop) - ((sibling.clientHeight | 0) << 1));
    }
  }
}
function $p_Ldotty_tools_scaladoc_SearchbarComponent__handleArrowDown__V($thiz) {
  var selectedElement = $thiz.ag.querySelector("[selected]");
  if ((selectedElement !== null)) {
    var sibling = $p_Ldotty_tools_scaladoc_SearchbarComponent__recur$2__Lorg_scalajs_dom_Element__Lorg_scalajs_dom_Element($thiz, selectedElement);
    if ((sibling !== null)) {
      selectedElement.removeAttribute("selected");
      sibling.setAttribute("selected", "");
      $thiz.ag.scrollTop = ((+sibling.offsetTop) - ((sibling.clientHeight | 0) << 1));
    }
  } else {
    var firstResult = $thiz.ag.firstElementChild;
    if ((firstResult !== null)) {
      var toSelect = (((!(!firstResult.classList.contains("scaladoc-searchbar-row"))) && (!(!firstResult.hasAttribute("result")))) ? firstResult : $p_Ldotty_tools_scaladoc_SearchbarComponent__recur$2__Lorg_scalajs_dom_Element__Lorg_scalajs_dom_Element($thiz, firstResult));
      toSelect.setAttribute("selected", "");
      $thiz.ag.scrollTop = ((+toSelect.offsetTop) - ((toSelect.clientHeight | 0) << 1));
    }
  }
}
function $p_Ldotty_tools_scaladoc_SearchbarComponent__handleEnter__V($thiz) {
  var selectedElement = $thiz.ag.querySelector("[selected] a");
  if ((selectedElement !== null)) {
    selectedElement.click();
  }
}
function $p_Ldotty_tools_scaladoc_SearchbarComponent__handleEscape__Lorg_scalajs_dom_Node($thiz) {
  $thiz.cs.value = "";
  $thiz.cs.dispatchEvent(new Event("input"));
  return document.body.removeChild($thiz.bX);
}
function $p_Ldotty_tools_scaladoc_SearchbarComponent__handleHover__Lorg_scalajs_dom_HTMLElement__V($thiz, elem) {
  var selectedElement = $thiz.ag.querySelector("[selected]");
  if ((selectedElement !== null)) {
    selectedElement.removeAttribute("selected");
  }
  elem.setAttribute("selected", "");
}
function $p_Ldotty_tools_scaladoc_SearchbarComponent__handleGlobalKeyDown__Lorg_scalajs_dom_KeyboardEvent__V($thiz, e) {
  if (((e.key === "s") || (e.key === "/"))) {
    var tag = e.target.tagName;
    if (((tag !== "INPUT") && (tag !== "TEXTAREA"))) {
      if ((!(!(!document.body.contains($thiz.bX))))) {
        e.preventDefault();
        document.body.appendChild($thiz.bX);
        window.setTimeout((() => {
          $thiz.cs.focus();
        }), 1.0);
      }
    }
  }
}
function $p_Ldotty_tools_scaladoc_SearchbarComponent__showHints__Lorg_scalajs_dom_Node($thiz) {
  var hintsDiv = $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "searchbar-hints")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().b0.cm($ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "lightbulb"), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([])))), $m_Ldotty_tools_scaladoc_utils_HTML$().i3.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "body-medium")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["A bunch of search hints to make your life easier"])))), $m_Ldotty_tools_scaladoc_utils_HTML$().lD.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "searchbar-hints-list")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().i3.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "h100")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Members or Static sites by any phrase"])))), $m_Ldotty_tools_scaladoc_utils_HTML$().f9.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "mono-small-inline")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Any phrase to find", $m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([" Name"])))), " or ", $m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Title"])))), ""])))), $m_Ldotty_tools_scaladoc_utils_HTML$().ay.cm($ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "divider"), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([])))), $m_Ldotty_tools_scaladoc_utils_HTML$().f9.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "mono-small-inline")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["cC, caCa, camCa", $m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([" to find"])))), " camelCase"])))), $m_Ldotty_tools_scaladoc_utils_HTML$().i3.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "h100")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Members by signature"])))), $m_Ldotty_tools_scaladoc_utils_HTML$().f9.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "mono-small-inline")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["String => Int", $m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([" to find"])))), " String.size, String.toInt"])))), $m_Ldotty_tools_scaladoc_utils_HTML$().ay.cm($ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "divider"), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([])))), $m_Ldotty_tools_scaladoc_utils_HTML$().f9.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "mono-small-inline")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["String => String => String", $m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([" to find "])))), "String.mkString, String.stripPrefix"])))), $m_Ldotty_tools_scaladoc_utils_HTML$().ay.cm($ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "divider"), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([])))), $m_Ldotty_tools_scaladoc_utils_HTML$().f9.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "mono-small-inline")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Some[A] => A", $m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([" to find"])))), " Some.value"])))), $m_Ldotty_tools_scaladoc_utils_HTML$().f9.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "link body-small")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Availability of searching by inkuire depends on the configuration of Scaladoc. For more info, ", $m_Ldotty_tools_scaladoc_utils_HTML$().dZ.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().fY.v, "https://docs.scala-lang.org/scala3/guides/scaladoc/search-engine.html")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["the documentation"]))))]))))]))))]))));
  return $thiz.ag.appendChild(hintsDiv);
}
function $p_Ldotty_tools_scaladoc_SearchbarComponent__createLoadMoreElement$1__Lorg_scalajs_dom_HTMLDivElement($thiz) {
  var a = $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "scaladoc-searchbar-row mono-small-inline"), ($m_Ldotty_tools_scaladoc_utils_HTML$(), $ct_T2__O__O__(new $c_T2(), "loadmore", ""))]), new $c_sjsr_WrappedVarArgs([$m_Ldotty_tools_scaladoc_utils_HTML$().dZ.Z(new $c_sjsr_WrappedVarArgs([$m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs(["Load more"]))]))]));
  a.addEventListener("mouseover", ((_$8$2) => {
    $p_Ldotty_tools_scaladoc_SearchbarComponent__handleHover__Lorg_scalajs_dom_HTMLElement__V($thiz, a);
  }));
  return a;
}
function $p_Ldotty_tools_scaladoc_SearchbarComponent__loadMoreResults$1__Lorg_scalajs_dom_HTMLDivElement__sci_List__V($thiz, loadMoreElement$2, entries) {
  loadMoreElement$2.onclick = ((event$2) => {
    var this$2 = entries.oY($thiz.jf);
    var these = this$2;
    while ((!these.g())) {
      var x0 = these.m();
      x0.classList.remove("hidden");
      these = these.o();
    }
    var nextElems = $p_sc_StrictOptimizedLinearSeqOps__loop$2__I__sc_LinearSeq__sc_LinearSeq(entries, $thiz.jf, entries);
    return ((!nextElems.g()) ? ($p_Ldotty_tools_scaladoc_SearchbarComponent__loadMoreResults$1__Lorg_scalajs_dom_HTMLDivElement__sci_List__V($thiz, loadMoreElement$2, nextElems), (void 0)) : (loadMoreElement$2.classList.add("hidden"), (void 0)));
  });
}
function $p_Ldotty_tools_scaladoc_SearchbarComponent__recur$1__Lorg_scalajs_dom_Element__Lorg_scalajs_dom_Element($thiz, elem) {
  var elem$tailLocal1 = elem;
  while (true) {
    var prev = elem$tailLocal1.previousElementSibling;
    if ((prev === null)) {
      return null;
    } else if ((((!(!(!prev.classList.contains("hidden")))) && (!(!prev.classList.contains("scaladoc-searchbar-row")))) && ((!(!prev.hasAttribute("result"))) || (!(!prev.hasAttribute("loadmore")))))) {
      return prev;
    } else {
      elem$tailLocal1 = prev;
    }
  }
}
function $p_Ldotty_tools_scaladoc_SearchbarComponent__recur$2__Lorg_scalajs_dom_Element__Lorg_scalajs_dom_Element($thiz, elem) {
  var elem$tailLocal2 = elem;
  while (true) {
    var next = elem$tailLocal2.nextElementSibling;
    if ((next === null)) {
      return null;
    } else if ((((!(!(!next.classList.contains("hidden")))) && (!(!next.classList.contains("scaladoc-searchbar-row")))) && ((!(!next.hasAttribute("result"))) || (!(!next.hasAttribute("loadmore")))))) {
      return next;
    } else {
      elem$tailLocal2 = next;
    }
  }
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_SearchbarComponent(engine, inkuireEngine, parser) {
  this.lr = null;
  this.lt = null;
  this.lu = null;
  this.ls = 0;
  this.jf = 0;
  this.je = null;
  this.jg = null;
  this.lv = null;
  this.cs = null;
  this.ag = null;
  this.bX = null;
  this.lr = engine;
  this.lt = inkuireEngine;
  this.lu = parser;
  this.ls = 5;
  this.jf = 20;
  var this$1 = $m_s_Option$().hB(new URLSearchParams(window.location.search).get("search"));
  if (this$1.g()) {
    var $x_1 = true;
  } else {
    var x0 = this$1.S();
    $m_sc_StringOps$();
    var $x_1 = (x0 !== "");
  }
  this.je = ($x_1 ? this$1 : $m_s_None$());
  this.jg = null;
  var icon = document.getElementById("search-toggle");
  icon.onclick = ((event$2) => ((!(!document.body.contains(this.bX))) ? document.body.removeChild(this.bX) : (document.body.appendChild(this.bX), this.cs.focus(), (void 0))));
  var \u03b41$ = document.body;
  \u03b41$.addEventListener("keydown", ((e$2) => {
    $p_Ldotty_tools_scaladoc_SearchbarComponent__handleGlobalKeyDown__Lorg_scalajs_dom_KeyboardEvent__V(this, e$2);
  }));
  this.lv = icon;
  var mobileSearch = document.getElementById("mobile-scaladoc-searchbar-input");
  mobileSearch.onfocus = ((event$2$1) => ((!(!document.body.contains(this.bX))) ? (void 0) : (document.body.appendChild(this.bX), this.cs.focus(), (void 0))));
  var \u03b42$ = document.body;
  \u03b42$.addEventListener("keydown", ((e$2$1) => {
    $p_Ldotty_tools_scaladoc_SearchbarComponent__handleGlobalKeyDown__Lorg_scalajs_dom_KeyboardEvent__V(this, e$2$1);
  }));
  var this$8 = this.je;
  var initialValue = (this$8.g() ? "" : this$8.S());
  var a = $m_Ldotty_tools_scaladoc_utils_HTML$().jh.cm($ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "scaladoc-searchbar-input"), new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().lC.v, "search"), $ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().lA.v, "Find anything"), $ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().lE.v, initialValue)]));
  a.addEventListener("input", ((e$2$2) => {
    $m_sjs_js_timers_package$().qb(this.jg);
    var inputValue = e$2$2.target.value;
    return ((inputValue === "") ? (this.nW(), ($m_Ldotty_tools_scaladoc_RecentQueryStorage$().g() ? $p_Ldotty_tools_scaladoc_SearchbarComponent__showHints__Lorg_scalajs_dom_Node(this) : (this.oq(""), (void 0)))) : (this.r7(inputValue), (void 0)));
  }));
  a.autocomplete = "off";
  this.cs = a;
  this.ag = $m_Ldotty_tools_scaladoc_utils_HTML$().ay.cm($ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().eu.v, "scaladoc-searchbar-results"), new $c_sjsr_WrappedVarArgs([]));
  this.bX = $p_Ldotty_tools_scaladoc_SearchbarComponent__generateRootDiv__Lorg_scalajs_dom_HTMLDivElement(this);
  this.cs.dispatchEvent(new Event("input"));
  if (((!this.je.g()) && (!(!(!document.body.contains(this.bX)))))) {
    document.body.appendChild(this.bX);
    this.cs.focus();
  }
}
$p = $c_Ldotty_tools_scaladoc_SearchbarComponent.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_SearchbarComponent;
/** @constructor */
function $h_Ldotty_tools_scaladoc_SearchbarComponent() {
}
$h_Ldotty_tools_scaladoc_SearchbarComponent.prototype = $p;
$p.oD = (function() {
  return window.document.documentElement.getAttribute("data-pathToRoot");
});
$p.si = (function(p, boldChars) {
  var location = (p.fU ? p.f8 : (("" + this.oD()) + p.f8));
  var extensionTargetMessage = ((p.f6 === "") ? "" : (" extension on " + p.f6));
  return $m_s_util_ChainingOps$().f4($m_s_util_ChainingOps$().f4($m_Ldotty_tools_scaladoc_utils_HTML$().dZ.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "scaladoc-searchbar-row mono-small-inline"), $ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().fY.v, location)]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$f_sc_IterableOps__zipWithIndex__O($m_s_Predef$().ld(p.fT)).aa(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$2) => {
    var c = $uC(x$1$2.aw());
    var i = x$1$2.kk();
    return ((c === 32) ? $m_Ldotty_tools_scaladoc_utils_HTML$().pC("&nbsp;") : (boldChars.bb(i) ? $m_Ldotty_tools_scaladoc_utils_HTML$().ce.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([("" + $cToS(c))])))) : ("" + $cToS(c))));
  }))), $m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().ly.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([extensionTargetMessage]))))])))), $m_Ldotty_tools_scaladoc_utils_HTML$().b0.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "pull-right scaladoc-searchbar-location")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([p.fS])))), ((p.f7 === "") ? "" : $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "scaladoc-searchbar-extra-info")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([p.f7])))))])))), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$2$3) => {
    _$2$3.onclick = ((event$2) => ((!(!document.body.contains(this.bX))) ? (document.body.removeChild(this.bX), (void 0)) : (void 0)));
  }))), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((wrapper$3) => {
    wrapper$3.addEventListener("mouseover", ((x$1$2$1) => {
      matchResult1: {
        if ((!(!(x$1$2$1 instanceof MouseEvent)))) {
          $p_Ldotty_tools_scaladoc_SearchbarComponent__handleHover__Lorg_scalajs_dom_HTMLElement__V(this, wrapper$3);
          break matchResult1;
        }
        throw new $c_s_MatchError(x$1$2$1);
      }
    }));
  })));
});
$p.p1 = (function(m) {
  if ((($m_sc_StringOps$(), m.es.charCodeAt(0)) === 101)) {
    var this$3 = m.es;
    var location = this$3.substring(1);
  } else {
    var $x_1 = this.oD();
    var this$6 = m.es;
    var location = (("" + $x_1) + this$6.substring(1));
  }
  return $m_s_util_ChainingOps$().f4($m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "scaladoc-searchbar-row mono-small-inline"), ($m_Ldotty_tools_scaladoc_utils_HTML$(), $ct_T2__O__O__(new $c_T2(), "result", "")), ($m_Ldotty_tools_scaladoc_utils_HTML$(), $ct_T2__O__O__(new $c_T2(), "inkuire-result", "")), ($m_Ldotty_tools_scaladoc_utils_HTML$(), $ct_T2__O__O__(new $c_T2(), "mq", ("" + m.f5)))]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_s_util_ChainingOps$().f4($m_Ldotty_tools_scaladoc_utils_HTML$().dZ.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().fY.v, location)]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([m.fP, $m_Ldotty_tools_scaladoc_utils_HTML$().b0.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "pull-right scaladoc-searchbar-inkuire-signature")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([m.fR]))))])))), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$3$3) => {
    _$3$3.onclick = ((event$2) => ((!(!document.body.contains(this.bX))) ? (document.body.removeChild(this.bX), (void 0)) : (void 0)));
  }))), $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "scaladoc-searchbar-inkuire-package")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().b0.cm($ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "micon pa"), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([])))), $m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([m.fQ]))))]))))])))), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((wrapper$3) => {
    wrapper$3.addEventListener("mouseover", ((x$1$2) => {
      matchResult2: {
        if ((!(!(x$1$2 instanceof MouseEvent)))) {
          $p_Ldotty_tools_scaladoc_SearchbarComponent__handleHover__Lorg_scalajs_dom_HTMLElement__V(this, wrapper$3);
          break matchResult2;
        }
        throw new $c_s_MatchError(x$1$2);
      }
    }));
  })));
});
$p.sj = (function(rq) {
  var a = $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "scaladoc-searchbar-row mono-small-inline"), ($m_Ldotty_tools_scaladoc_utils_HTML$(), $ct_T2__O__O__(new $c_T2(), "result", ""))]), new $c_sjsr_WrappedVarArgs([$m_Ldotty_tools_scaladoc_utils_HTML$().dZ.Z(new $c_sjsr_WrappedVarArgs([$m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs([rq.query]))]))]));
  a.addEventListener("click", ((_$5$2) => {
    this.cs.value = rq.query;
    return (!(!this.cs.dispatchEvent(new Event("input"))));
  }));
  a.addEventListener("mouseover", ((x$1$2) => {
    matchResult3: {
      if ((!(!(x$1$2 instanceof MouseEvent)))) {
        $p_Ldotty_tools_scaladoc_SearchbarComponent__handleHover__Lorg_scalajs_dom_HTMLElement__V(this, a);
        break matchResult3;
      }
      throw new $c_s_MatchError(x$1$2);
    }
  }));
  return a;
});
$p.kx = (function(kind, customClass) {
  var $x_3 = $m_Ldotty_tools_scaladoc_utils_HTML$().ay;
  var $x_2 = new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "scaladoc-searchbar-row mono-small-inline"), ($m_Ldotty_tools_scaladoc_utils_HTML$(), $ct_T2__O__O__(new $c_T2(), "divider", ""))]);
  var $x_1 = $m_Ldotty_tools_scaladoc_utils_HTML$().b0;
  var this$9 = $m_Ldotty_tools_scaladoc_utils_HTML$().A;
  var value = ((("micon " + $m_sc_StringOps$().oZ(kind, 2)) + " ") + customClass);
  return $x_3.D($x_2, new $c_sjsr_WrappedVarArgs([$x_1.cm($ct_T2__O__O__(new $c_T2(), this$9.v, value), new $c_sjsr_WrappedVarArgs([])), $m_Ldotty_tools_scaladoc_utils_HTML$().b0.Z(new $c_sjsr_WrappedVarArgs([kind]))]));
});
$p.r6 = (function(query) {
  return $m_s_concurrent_Future$().q3(new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => this.lr.rJ(query))), $m_s_concurrent_ExecutionContext$().iX()).oz(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((result$2) => {
    if (result$2.g()) {
      var noResultsDiv = $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().eu.v, "no-results-container")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O([$m_Ldotty_tools_scaladoc_utils_HTML$().ay.cm($ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "no-result-icon"), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([])))), $m_Ldotty_tools_scaladoc_utils_HTML$().lx.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "h200 no-result-header")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["No results match your filter criteria."])))), $m_Ldotty_tools_scaladoc_utils_HTML$().ji.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "body-small no-result-content")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Try adjusting or clearing your filters", $m_Ldotty_tools_scaladoc_utils_HTML$().ji.Z(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["to display better result"]))))])))), $m_s_util_ChainingOps$().f4($m_Ldotty_tools_scaladoc_utils_HTML$().i2.D(new $c_sjsr_WrappedVarArgs($m_sjsr_package$().I(new ($d_s_Equals.r().C)([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().eu.v, "searchbar-clear-button"), $ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "clearButton label-only-button")]))), new $c_sjsr_WrappedVarArgs($m_sjsr_package$().z(new $ac_O(["Clear all filters"])))), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$6$3) => {
        _$6$3.addEventListener("click", ((_$7$2) => {
          this.cs.value = "";
          return (!(!this.cs.dispatchEvent(new Event("input"))));
        }));
      })))]))));
      this.ag.scrollTop = 0.0;
      return this.ag.appendChild(noResultsDiv);
    } else {
      var resultWithDocBonus = result$2.hN(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((entry$2) => {
        if ((entry$2.bW.dw === "static")) {
          var this$47 = window.location.href;
          var $x_1 = (!((this$47.indexOf("api") | 0) !== (-1)));
        } else {
          var $x_1 = false;
        }
        if ($x_1) {
          return new $c_Ldotty_tools_scaladoc_MatchResult(((7 + entry$2.cK) | 0), entry$2.bW, entry$2.dv);
        } else {
          return entry$2;
        }
      })));
      var fragment = document.createDocumentFragment();
      $f_sc_SeqOps__sortBy__F1__s_math_Ordering__O($f_sc_IterableOps__groupBy__F1__sci_Map(resultWithDocBonus, new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$9$2) => _$9$2.bW.dw))).aa(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$2) => {
        if ((x$1$2 !== null)) {
          var kind = x$1$2.aw();
          var results = x$1$2.ap();
          var this$48 = $f_sc_IterableOnceOps__maxByOption__F1__s_math_Ordering__s_Option(results, new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$10$2) => _$10$2.cK)), $m_s_math_Ordering$Int$());
          var f$2 = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$11$2) => _$11$2.cK));
          return new $c_T3(kind, (this$48.g() ? $m_s_None$() : new $c_s_Some(f$2.d(this$48.S()))), results);
        }
        throw new $c_s_MatchError(x$1$2);
      }))).c2(), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$2$1) => {
        if ((x$1$2$1 !== null)) {
          var topScore = x$1$2$1.g6;
          var default$1 = new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => 0));
          return ((-((topScore.g() ? default$1.a2() : topScore.S()) | 0)) | 0);
        }
        throw new $c_s_MatchError(x$1$2$1);
      })), $m_s_math_Ordering$Int$()).hN(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$2$2) => {
        if ((x$1$2$2 !== null)) {
          return $ct_T2__O__O__(new $c_T2(), x$1$2$2.g5, x$1$2$2.g7.oY(40));
        }
        throw new $c_s_MatchError(x$1$2$2);
      }))).hN(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$3) => {
        matchResult7: {
          if ((x$1$3 !== null)) {
            var kind$2 = x$1$3.aw();
            var results$2 = x$1$3.ap();
            var kindSeparator = this.kx(kind$2, "");
            var htmlEntries = results$2.hN(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((result$2$1) => this.si(result$2$1.bW, result$2$1.dv))));
            var loadMoreElement = $p_Ldotty_tools_scaladoc_SearchbarComponent__createLoadMoreElement$1__Lorg_scalajs_dom_HTMLDivElement(this);
            fragment.appendChild(kindSeparator);
            var f$4 = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((newChild$2) => fragment.appendChild(newChild$2)));
            var these = htmlEntries;
            while ((!these.g())) {
              f$4.d(these.m());
              these = these.o();
            }
            fragment.appendChild(loadMoreElement);
            var nextElems = $p_sc_StrictOptimizedLinearSeqOps__loop$2__I__sc_LinearSeq__sc_LinearSeq(htmlEntries, this.ls, htmlEntries);
            if ((!nextElems.g())) {
              var f$5 = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$13$3) => {
                _$13$3.classList.add("hidden");
              }));
              var these$1 = nextElems;
              while ((!these$1.g())) {
                f$5.d(these$1.m());
                these$1 = these$1.o();
              }
              $p_Ldotty_tools_scaladoc_SearchbarComponent__loadMoreResults$1__Lorg_scalajs_dom_HTMLDivElement__sci_List__V(this, loadMoreElement, nextElems);
              break matchResult7;
            } else {
              loadMoreElement.classList.add("hidden");
              break matchResult7;
            }
          }
          throw new $c_s_MatchError(x$1$3);
        }
      })));
      this.ag.scrollTop = 0.0;
      return this.ag.appendChild(fragment);
    }
  })), $m_s_concurrent_ExecutionContext$().iX());
});
$p.oq = (function(query) {
  var recentQueries = $m_Ldotty_tools_scaladoc_RecentQueryStorage$().iW();
  if ((query !== "")) {
    $m_Ldotty_tools_scaladoc_RecentQueryStorage$().pM(new ($a_Ldotty_tools_scaladoc_RecentQuery())(query, (+Date.now())));
  }
  var res = [];
  var len = (recentQueries.length | 0);
  var i = 0;
  while ((i < len)) {
    var x0 = recentQueries[i];
    var this$6 = x0.query;
    _return: {
      var len$1 = this$6.length;
      if ((!((query === null) || (query.length !== len$1)))) {
        var i$1 = 0;
        while ((i$1 !== len$1)) {
          var thisCP = $f_T__codePointAt__I__I(this$6, i$1);
          if (($m_jl_Character$().hV($m_jl_Character$().jb(thisCP)) !== $m_jl_Character$().hV($m_jl_Character$().jb($f_T__codePointAt__I__I(query, i$1))))) {
            var $x_1 = false;
            break _return;
          }
          i$1 = ((i$1 + ((thisCP >= 65536) ? 2 : 1)) | 0);
        }
        var $x_1 = true;
      } else {
        var $x_1 = false;
      }
    }
    if ((!$x_1)) {
      (res.push(x0) | 0);
    }
    i = ((1 + i) | 0);
  }
  var res$1 = [];
  var len$2 = (res.length | 0);
  var i$2 = 0;
  while ((i$2 < len$2)) {
    var x0$1 = res[i$2];
    $m_sc_StringOps$();
    var x = x0$1.query;
    var v = query;
    var i$3 = 0;
    var len$3 = x.length;
    while ((i$3 < len$3)) {
      var x0$2 = v;
      var index = i$3;
      var x1 = x.charCodeAt(index);
      if ((x0$2 !== "")) {
        var this$15 = $m_sc_StringOps$().r9(x0$2);
        var this$18 = ("" + $cToS(this$15));
        var anotherString = ("" + $cToS(x1));
        _return$1: {
          var len$4 = this$18.length;
          if ((anotherString.length === len$4)) {
            var i$4 = 0;
            while ((i$4 !== len$4)) {
              var thisCP$1 = $f_T__codePointAt__I__I(this$18, i$4);
              if (($m_jl_Character$().hV($m_jl_Character$().jb(thisCP$1)) !== $m_jl_Character$().hV($m_jl_Character$().jb($f_T__codePointAt__I__I(anotherString, i$4))))) {
                var $x_2 = false;
                break _return$1;
              }
              i$4 = ((i$4 + ((thisCP$1 >= 65536) ? 2 : 1)) | 0);
            }
            var $x_2 = true;
          } else {
            var $x_2 = false;
          }
        }
        if ($x_2) {
          v = $m_sc_StringOps$().j9(x0$2);
        } else {
          v = x0$2;
        }
      } else {
        v = "";
      }
      i$3 = ((1 + i$3) | 0);
    }
    if ((v === "")) {
      (res$1.push(x0$1) | 0);
    }
    i$2 = ((1 + i$2) | 0);
  }
  if (((res$1.length | 0) !== 0)) {
    this.ag.appendChild(this.kx("Recently searched", ""));
    var len$5 = (res$1.length | 0);
    var res$2 = new Array(len$5);
    var i$5 = 0;
    while ((i$5 < len$5)) {
      res$2[i$5] = this.sj(res$1[i$5]);
      i$5 = ((1 + i$5) | 0);
    }
    var len$6 = (res$2.length | 0);
    var i$6 = 0;
    while ((i$6 < len$6)) {
      var x0$4 = res$2[i$6];
      this.ag.appendChild(x0$4);
      i$6 = ((1 + i$6) | 0);
    }
  }
});
$p.qp = (function() {
  return $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "loading-wrapper")]), new $c_sjsr_WrappedVarArgs([$m_Ldotty_tools_scaladoc_utils_HTML$().ay.cm($ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "loading"), new $c_sjsr_WrappedVarArgs([]))]));
});
$p.sk = (function(s) {
  return $m_Ldotty_tools_scaladoc_utils_HTML$().ay.D(new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "scaladoc-searchbar-row mono-small-inline"), ($m_Ldotty_tools_scaladoc_utils_HTML$(), $ct_T2__O__O__(new $c_T2(), "error", ""))]), new $c_sjsr_WrappedVarArgs([$m_Ldotty_tools_scaladoc_utils_HTML$().b0.D(new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $m_Ldotty_tools_scaladoc_utils_HTML$().A.v, "search-error")]), new $c_sjsr_WrappedVarArgs([s]))]));
});
$p.r7 = (function(query) {
  this.ag.scrollTop = 0.0;
  this.ag.onscroll = ((event$2) => (void 0));
  document.createDocumentFragment();
  var $x_1 = $m_sjs_js_timers_package$();
  var this$3 = new $c_s_concurrent_duration_package$DurationInt(($m_s_concurrent_duration_package$(), 300));
  var unit = $m_s_concurrent_duration_package$().iB;
  var this$ = this$3.hj;
  $m_s_concurrent_duration_Duration$();
  var hi = (this$ >> 31);
  this.jg = $x_1.s3(new $c_s_concurrent_duration_FiniteDuration(this$, hi, unit), new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => {
    this.nW();
    this.oq(query);
    matchResult8: {
      var x21 = this.lu.rF(query);
      if ((x21 instanceof $c_Ldotty_tools_scaladoc_NameAndKindQuery)) {
        this.r6(x21);
        break matchResult8;
      }
      if ((x21 instanceof $c_Ldotty_tools_scaladoc_SignatureQuery)) {
        var loading = this.qp();
        var kindSeparator = this.kx("inkuire", "");
        this.ag.appendChild(loading);
        this.ag.appendChild(kindSeparator);
        this.lt.rK(query, new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((m$3) => {
          var this$10 = new $c_Lorg_scalajs_dom_DOMList$DOMListSeq(this.ag.children);
          _return: {
            var it = this$10.c();
            while (it.k()) {
              var a = it.f();
              if (((!(!a.hasAttribute("mq"))) && ($m_jl_Integer$().kU(a.getAttribute("mq"), 10, 214748364) > m$3.f5))) {
                var next = new $c_s_Some(a);
                break _return;
              }
            }
            var next = $m_s_None$();
          }
          if (next.g()) {
            this.ag.appendChild(this.p1(m$3));
          } else {
            var x0 = next.S();
            this.ag.insertBefore(this.p1(m$3), x0);
          }
        })), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((s$3) => {
          this.ag.removeChild(loading);
          this.ag.appendChild(this.sk(s$3));
        })));
        break matchResult8;
      }
      throw new $c_s_MatchError(x21);
    }
  })));
});
$p.nW = (function() {
  while ((!(!this.ag.hasChildNodes()))) {
    this.ag.removeChild(this.ag.lastChild);
  }
});
var $d_Ldotty_tools_scaladoc_SearchbarComponent = new $TypeData().i($c_Ldotty_tools_scaladoc_SearchbarComponent, "dotty.tools.scaladoc.SearchbarComponent", ({
  de: 1
}));
/** @constructor */
function $c_Ldotty_tools_scaladoc_StringUtils$() {
}
$p = $c_Ldotty_tools_scaladoc_StringUtils$.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_StringUtils$;
/** @constructor */
function $h_Ldotty_tools_scaladoc_StringUtils$() {
}
$h_Ldotty_tools_scaladoc_StringUtils$.prototype = $p;
$p.kw = (function(s) {
  if ((s === "")) {
    $m_sci_List$();
    return $m_sci_Nil$();
  } else {
    var x = $m_sc_StringOps$().j9(s);
    $m_sc_StringOps$();
    $m_sc_StringOps$();
    var from = 0;
    _return: {
      var len = x.length;
      var i = from;
      while ((i < len)) {
        if ($m_jl_UnicodeData$().kT(x.charCodeAt(i))) {
          var $x_1 = i;
          break _return;
        }
        i = ((1 + i) | 0);
      }
      var $x_1 = (-1);
    }
    if (($x_1 === (-1))) {
      return new $c_sci_$colon$colon(s, $m_sci_Nil$());
    } else {
      var $x_6 = $m_sc_StringOps$();
      var x$1 = $m_sc_StringOps$().j9(s);
      $m_sc_StringOps$();
      $m_sc_StringOps$();
      var from$1 = 0;
      _return$1: {
        var len$1 = x$1.length;
        var i$1 = from$1;
        while ((i$1 < len$1)) {
          if ($m_jl_UnicodeData$().kT(x$1.charCodeAt(i$1))) {
            var $x_5 = i$1;
            break _return$1;
          }
          i$1 = ((1 + i$1) | 0);
        }
        var $x_5 = (-1);
      }
      var $x_4 = new $c_sci_$colon$colon($x_6.oZ(s, ((1 + $x_5) | 0)), $m_sci_Nil$());
      var $x_3 = $m_sc_StringOps$();
      var x$2 = $m_sc_StringOps$().j9(s);
      $m_sc_StringOps$();
      $m_sc_StringOps$();
      var from$2 = 0;
      _return$2: {
        var len$2 = x$2.length;
        var i$2 = from$2;
        while ((i$2 < len$2)) {
          if ($m_jl_UnicodeData$().kT(x$2.charCodeAt(i$2))) {
            var $x_2 = i$2;
            break _return$2;
          }
          i$2 = ((1 + i$2) | 0);
        }
        var $x_2 = (-1);
      }
      return $x_4.pZ(this.kw($x_3.o8(s, ((1 + $x_2) | 0))));
    }
  }
});
var $d_Ldotty_tools_scaladoc_StringUtils$ = new $TypeData().i($c_Ldotty_tools_scaladoc_StringUtils$, "dotty.tools.scaladoc.StringUtils$", ({
  df: 1
}));
var $n_Ldotty_tools_scaladoc_StringUtils$;
function $m_Ldotty_tools_scaladoc_StringUtils$() {
  if ((!$n_Ldotty_tools_scaladoc_StringUtils$)) {
    $n_Ldotty_tools_scaladoc_StringUtils$ = new $c_Ldotty_tools_scaladoc_StringUtils$();
  }
  return $n_Ldotty_tools_scaladoc_StringUtils$;
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_TooltipNormalizer() {
  document.addEventListener("mouseover", ((e$2) => {
    if ((!(!(!(!e$2.target.closest(".snippet-error.tooltip")))))) {
      var tooltipParent = e$2.target;
      var tooltipContainer = tooltipParent.querySelector(".tooltip-container");
      if (((typeof tooltipContainer) === "undefined")) {
      } else if ((!(!(tooltipContainer instanceof HTMLSpanElement)))) {
        var a = (+tooltipParent.offsetLeft);
        var $x_1 = $m_RTLong$().kL((+Math.round(a)));
        tooltipContainer.style = ("left: " + $s_RTLong__toString__I__I__T($x_1.l, $x_1.h));
      }
      return (void 0);
    } else {
      return (void 0);
    }
  }));
}
$p = $c_Ldotty_tools_scaladoc_TooltipNormalizer.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_TooltipNormalizer;
/** @constructor */
function $h_Ldotty_tools_scaladoc_TooltipNormalizer() {
}
$h_Ldotty_tools_scaladoc_TooltipNormalizer.prototype = $p;
var $d_Ldotty_tools_scaladoc_TooltipNormalizer = new $TypeData().i($c_Ldotty_tools_scaladoc_TooltipNormalizer, "dotty.tools.scaladoc.TooltipNormalizer", ({
  dg: 1
}));
/** @constructor */
function $c_Ldotty_tools_scaladoc_utils_HTML$() {
  this.ay = null;
  this.b0 = null;
  this.dZ = null;
  this.ji = null;
  this.i3 = null;
  this.lx = null;
  this.i2 = null;
  this.jh = null;
  this.lz = null;
  this.lD = null;
  this.f9 = null;
  this.lw = null;
  this.lB = null;
  this.ce = null;
  this.ly = null;
  this.A = null;
  this.fY = null;
  this.eu = null;
  this.lC = null;
  this.lA = null;
  this.lE = null;
  $n_Ldotty_tools_scaladoc_utils_HTML$ = this;
  this.ay = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("div");
  this.b0 = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("span");
  this.dZ = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("a");
  this.ji = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("p");
  this.i3 = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("h1");
  this.lx = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("h2");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("h3");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("h4");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("h5");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("h6");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("dl");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("dd");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("dt");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("svg");
  this.i2 = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("button");
  this.jh = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("input");
  this.lz = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("label");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("script");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("link");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("footer");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("html");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("head");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("meta");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("main");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("title");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("body");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("nav");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("img");
  this.lD = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("ul");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("ol");
  this.f9 = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("li");
  this.lw = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("code");
  this.lB = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("pre");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("table");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("thead");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("tbody");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("th");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("tr");
  $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("td");
  this.ce = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("b");
  this.ly = $m_Ldotty_tools_scaladoc_utils_HTML$Tag$().E("i");
  this.A = new $c_Ldotty_tools_scaladoc_utils_HTML$Attr("class");
  this.fY = new $c_Ldotty_tools_scaladoc_utils_HTML$Attr("href");
  this.eu = new $c_Ldotty_tools_scaladoc_utils_HTML$Attr("id");
  this.lC = new $c_Ldotty_tools_scaladoc_utils_HTML$Attr("type");
  this.lA = new $c_Ldotty_tools_scaladoc_utils_HTML$Attr("placeholder");
  this.lE = new $c_Ldotty_tools_scaladoc_utils_HTML$Attr("value");
}
$p = $c_Ldotty_tools_scaladoc_utils_HTML$.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_utils_HTML$;
/** @constructor */
function $h_Ldotty_tools_scaladoc_utils_HTML$() {
}
$h_Ldotty_tools_scaladoc_utils_HTML$.prototype = $p;
$p.pC = (function(content) {
  var x = document.createElement("a");
  x.innerHTML = content;
  return x;
});
var $d_Ldotty_tools_scaladoc_utils_HTML$ = new $TypeData().i($c_Ldotty_tools_scaladoc_utils_HTML$, "dotty.tools.scaladoc.utils.HTML$", ({
  dh: 1
}));
var $n_Ldotty_tools_scaladoc_utils_HTML$;
function $m_Ldotty_tools_scaladoc_utils_HTML$() {
  if ((!$n_Ldotty_tools_scaladoc_utils_HTML$)) {
    $n_Ldotty_tools_scaladoc_utils_HTML$ = new $c_Ldotty_tools_scaladoc_utils_HTML$();
  }
  return $n_Ldotty_tools_scaladoc_utils_HTML$;
}
/** @constructor */
function $c_jl_BoundsChecks$() {
}
$p = $c_jl_BoundsChecks$.prototype = new $h_O();
$p.constructor = $c_jl_BoundsChecks$;
/** @constructor */
function $h_jl_BoundsChecks$() {
}
$h_jl_BoundsChecks$.prototype = $p;
$p.rg = (function(index, length) {
  throw $ct_jl_IndexOutOfBoundsException__T__(new $c_jl_IndexOutOfBoundsException(), (((("Index " + index) + " out of bounds [0, ") + length) + "]"));
});
var $d_jl_BoundsChecks$ = new $TypeData().i($c_jl_BoundsChecks$, "java.lang.BoundsChecks$", ({
  dq: 1
}));
var $n_jl_BoundsChecks$;
function $m_jl_BoundsChecks$() {
  if ((!$n_jl_BoundsChecks$)) {
    $n_jl_BoundsChecks$ = new $c_jl_BoundsChecks$();
  }
  return $n_jl_BoundsChecks$;
}
var $d_jl_Runnable = new $TypeData().i(1, "java.lang.Runnable", ({
  aS: 1
}));
function $p_jl_StackTrace$__normalizedLinesToStackTrace__O__Ajl_StackTraceElement($thiz, lines) {
  var NormalizedFrameLine = $m_jl_StackTrace$StringRE$().ds("^([^@]*)@(.*?):([0-9]+)(?::([0-9]+))?$");
  var trace = [];
  var i = 0;
  while ((i < (lines.length | 0))) {
    var line = lines[i];
    if ((line !== "")) {
      var mtch = NormalizedFrameLine.exec(line);
      if ((mtch !== null)) {
        var classAndMethodName = $p_jl_StackTrace$__extractClassMethod__T__O($thiz, mtch[1]);
        var $x_5 = classAndMethodName[0];
        var $x_4 = classAndMethodName[1];
        var $x_3 = mtch[2];
        var x$2 = mtch[3];
        var $x_2 = parseInt(x$2);
        var x$3 = mtch[4];
        var $x_1 = trace.push(new $c_jl_StackTraceElement($x_5, $x_4, $x_3, ($x_2 | 0), ((x$3 !== (void 0)) ? (parseInt(x$3) | 0) : (-1))));
      } else {
        (trace.push(new $c_jl_StackTraceElement("<jscode>", line, null, (-1), (-1))) | 0);
      }
    }
    i = ((1 + i) | 0);
  }
  var len = (trace.length | 0);
  var result = new ($d_jl_StackTraceElement.r().C)(len);
  i = 0;
  while ((i < len)) {
    result.a[i] = trace[i];
    i = ((1 + i) | 0);
  }
  return result;
}
function $p_jl_StackTrace$__extractClassMethod__T__O($thiz, functionName) {
  var PatBC = $m_jl_StackTrace$StringRE$().ds("^(?:Object\\.|\\[object Object\\]\\.|Module\\.)?\\$[bc]_([^\\.]+)(?:\\.prototype)?\\.([^\\.]+)$");
  var PatS = $m_jl_StackTrace$StringRE$().ds("^(?:Object\\.|\\[object Object\\]\\.|Module\\.)?\\$(?:ps?|s|f)_((?:_[^_]|[^_])+)__([^\\.]+)$");
  var PatCT = $m_jl_StackTrace$StringRE$().ds("^(?:Object\\.|\\[object Object\\]\\.|Module\\.)?\\$ct_((?:_[^_]|[^_])+)__([^\\.]*)$");
  var PatN = $m_jl_StackTrace$StringRE$().ds("^new (?:Object\\.|\\[object Object\\]\\.|Module\\.)?\\$c_([^\\.]+)$");
  var PatM = $m_jl_StackTrace$StringRE$().ds("^(?:Object\\.|\\[object Object\\]\\.|Module\\.)?\\$m_([^\\.]+)$");
  var matchBC = PatBC.exec(functionName);
  var matchBCOrS = ((matchBC !== null) ? matchBC : PatS.exec(functionName));
  if ((matchBCOrS !== null)) {
    return [$p_jl_StackTrace$__decodeClassName__T__T($thiz, matchBCOrS[1]), $p_jl_StackTrace$__decodeMethodName__T__T($thiz, matchBCOrS[2])];
  } else {
    var matchCT = PatCT.exec(functionName);
    var matchCTOrN = ((matchCT !== null) ? matchCT : PatN.exec(functionName));
    if ((matchCTOrN !== null)) {
      return [$p_jl_StackTrace$__decodeClassName__T__T($thiz, matchCTOrN[1]), "<init>"];
    } else {
      var matchM = PatM.exec(functionName);
      return ((matchM !== null) ? [$p_jl_StackTrace$__decodeClassName__T__T($thiz, matchM[1]), "<clinit>"] : ["<jscode>", functionName]);
    }
  }
}
function $p_jl_StackTrace$__decodeClassName__T__T($thiz, encodedName) {
  var dict = $p_jl_StackTrace$__decompressedClasses__O($thiz);
  if ((!(!$m_jl_Utils$Cache$().g4.call(dict, encodedName)))) {
    var dict$1 = $p_jl_StackTrace$__decompressedClasses__O($thiz);
    var base = dict$1[encodedName];
  } else {
    var base = $p_jl_StackTrace$__loop$1__I__T__T($thiz, 0, encodedName);
  }
  var this$3 = base.split("_").join(".");
  return this$3.split("\uff3f").join("_");
}
function $p_jl_StackTrace$__decompressedClasses$lzycompute__O($thiz) {
  if (((((1 & $thiz.cL) << 24) >> 24) === 0)) {
    var dict = ({});
    dict.O = "java_lang_Object";
    dict.T = "java_lang_String";
    var index = 0;
    while ((index <= 22)) {
      if ((index >= 2)) {
        var key = ("T" + index);
        var value = ("scala_Tuple" + index);
        dict[key] = value;
      }
      var key$1 = ("F" + index);
      var value$1 = ("scala_Function" + index);
      dict[key$1] = value$1;
      index = ((1 + index) | 0);
    }
    $thiz.jl = dict;
    $thiz.cL = (((1 | $thiz.cL) << 24) >> 24);
  }
  return $thiz.jl;
}
function $p_jl_StackTrace$__decompressedClasses__O($thiz) {
  return (((((1 & $thiz.cL) << 24) >> 24) === 0) ? $p_jl_StackTrace$__decompressedClasses$lzycompute__O($thiz) : $thiz.jl);
}
function $p_jl_StackTrace$__decompressedPrefixes$lzycompute__O($thiz) {
  if (((((2 & $thiz.cL) << 24) >> 24) === 0)) {
    var dict = ({});
    dict.sjsr_ = "scala_scalajs_runtime_";
    dict.sjs_ = "scala_scalajs_";
    dict.sci_ = "scala_collection_immutable_";
    dict.scm_ = "scala_collection_mutable_";
    dict.scg_ = "scala_collection_generic_";
    dict.sc_ = "scala_collection_";
    dict.sr_ = "scala_runtime_";
    dict.s_ = "scala_";
    dict.jl_ = "java_lang_";
    dict.ju_ = "java_util_";
    $thiz.jm = dict;
    $thiz.cL = (((2 | $thiz.cL) << 24) >> 24);
  }
  return $thiz.jm;
}
function $p_jl_StackTrace$__decompressedPrefixes__O($thiz) {
  return (((((2 & $thiz.cL) << 24) >> 24) === 0) ? $p_jl_StackTrace$__decompressedPrefixes$lzycompute__O($thiz) : $thiz.jm);
}
function $p_jl_StackTrace$__compressedPrefixes$lzycompute__O($thiz) {
  if (((((4 & $thiz.cL) << 24) >> 24) === 0)) {
    $thiz.jk = Object.keys($p_jl_StackTrace$__decompressedPrefixes__O($thiz));
    $thiz.cL = (((4 | $thiz.cL) << 24) >> 24);
  }
  return $thiz.jk;
}
function $p_jl_StackTrace$__compressedPrefixes__O($thiz) {
  return (((((4 & $thiz.cL) << 24) >> 24) === 0) ? $p_jl_StackTrace$__compressedPrefixes$lzycompute__O($thiz) : $thiz.jk);
}
function $p_jl_StackTrace$__decodeMethodName__T__T($thiz, encodedName) {
  if ((!(!encodedName.startsWith("init___")))) {
    return "<init>";
  } else {
    var methodNameLen = (encodedName.indexOf("__") | 0);
    return ((methodNameLen < 0) ? encodedName : encodedName.substring(0, methodNameLen));
  }
}
function $p_jl_StackTrace$__normalizeStackTraceLines__O__O($thiz, e) {
  return ((!(!(!(!(!e))))) ? [] : ((!(!(!(!(e.arguments && e.stack))))) ? $p_jl_StackTrace$__extractChrome__O__O($thiz, e) : ((!(!(!(!(e.stack && e.sourceURL))))) ? $p_jl_StackTrace$__extractSafari__O__O($thiz, e) : ((!(!(!(!(e.stack && e.number))))) ? $p_jl_StackTrace$__extractIE__O__O($thiz, e) : ((!(!(!(!(e.stack && e.fileName))))) ? $p_jl_StackTrace$__extractFirefox__O__O($thiz, e) : ((!(!(!(!(e.message && e["opera#sourceloc"]))))) ? ((!(!(!(!(!e.stacktrace))))) ? $p_jl_StackTrace$__extractOpera9__O__O($thiz, e) : ((!(!(!(!((e.message.indexOf("\n") > (-1.0)) && (e.message.split("\n").length > e.stacktrace.split("\n").length)))))) ? $p_jl_StackTrace$__extractOpera9__O__O($thiz, e) : $p_jl_StackTrace$__extractOpera10a__O__O($thiz, e))) : ((!(!(!(!((e.message && e.stack) && e.stacktrace))))) ? ((!(!(!(!(e.stacktrace.indexOf("called from line") < 0.0))))) ? $p_jl_StackTrace$__extractOpera10b__O__O($thiz, e) : $p_jl_StackTrace$__extractOpera11__O__O($thiz, e)) : ((!(!(!(!(e.stack && (!e.fileName)))))) ? $p_jl_StackTrace$__extractChrome__O__O($thiz, e) : $p_jl_StackTrace$__extractOther__O__O($thiz, e)))))))));
}
function $p_jl_StackTrace$__extractChrome__O__O($thiz, e) {
  return (e.stack + "\n").replace($m_jl_StackTrace$StringRE$().ds("^[\\s\\S]+?\\s+at\\s+"), " at ").replace($m_jl_StackTrace$StringRE$().cq("^\\s+(at eval )?at\\s+", "gm"), "").replace($m_jl_StackTrace$StringRE$().cq("^([^\\(]+?)([\\n])", "gm"), "{anonymous}() ($1)$2").replace($m_jl_StackTrace$StringRE$().cq("^Object.<anonymous>\\s*\\(([^\\)]+)\\)", "gm"), "{anonymous}() ($1)").replace($m_jl_StackTrace$StringRE$().cq("^([^\\(]+|\\{anonymous\\}\\(\\)) \\((.+)\\)$", "gm"), "$1@$2").split("\n").slice(0, (-1));
}
function $p_jl_StackTrace$__extractFirefox__O__O($thiz, e) {
  return e.stack.replace($m_jl_StackTrace$StringRE$().cq("(?:\\n@:0)?\\s+$", "m"), "").replace($m_jl_StackTrace$StringRE$().cq("^(?:\\((\\S*)\\))?@", "gm"), "{anonymous}($1)@").split("\n");
}
function $p_jl_StackTrace$__extractIE__O__O($thiz, e) {
  var qual$1 = e.stack.replace($m_jl_StackTrace$StringRE$().cq("^\\s*at\\s+(.*)$", "gm"), "$1").replace($m_jl_StackTrace$StringRE$().cq("^Anonymous function\\s+", "gm"), "{anonymous}() ").replace($m_jl_StackTrace$StringRE$().cq("^([^\\(]+|\\{anonymous\\}\\(\\))\\s+\\((.+)\\)$", "gm"), "$1@$2").split("\n");
  return qual$1.slice(1);
}
function $p_jl_StackTrace$__extractSafari__O__O($thiz, e) {
  return e.stack.replace($m_jl_StackTrace$StringRE$().cq("\\[native code\\]\\n", "m"), "").replace($m_jl_StackTrace$StringRE$().cq("^(?=\\w+Error\\:).*$\\n", "m"), "").replace($m_jl_StackTrace$StringRE$().cq("^@", "gm"), "{anonymous}()@").split("\n");
}
function $p_jl_StackTrace$__extractOpera9__O__O($thiz, e) {
  var lineRE = $m_jl_StackTrace$StringRE$().cq("Line (\\d+).*script (?:in )?(\\S+)", "i");
  var lines = e.message.split("\n");
  var result = [];
  var i = 2;
  var len = (lines.length | 0);
  while ((i < len)) {
    var mtch = lineRE.exec(lines[i]);
    if ((mtch !== null)) {
      (result.push(((("{anonymous}()@" + mtch[2]) + ":") + mtch[1])) | 0);
    }
    i = ((2 + i) | 0);
  }
  return result;
}
function $p_jl_StackTrace$__extractOpera10a__O__O($thiz, e) {
  var lineRE = $m_jl_StackTrace$StringRE$().cq("Line (\\d+).*script (?:in )?(\\S+)(?:: In function (\\S+))?$", "i");
  var lines = e.stacktrace.split("\n");
  var result = [];
  var i = 0;
  var len = (lines.length | 0);
  while ((i < len)) {
    var mtch = lineRE.exec(lines[i]);
    if ((mtch !== null)) {
      var x = mtch[3];
      var fnName = ((x !== (void 0)) ? x : "{anonymous}");
      (result.push(((((fnName + "()@") + mtch[2]) + ":") + mtch[1])) | 0);
    }
    i = ((2 + i) | 0);
  }
  return result;
}
function $p_jl_StackTrace$__extractOpera10b__O__O($thiz, e) {
  var lineRE = $m_jl_StackTrace$StringRE$().ds("^(.*)@(.+):(\\d+)$");
  var lines = e.stacktrace.split("\n");
  var result = [];
  var i = 0;
  var len = (lines.length | 0);
  while ((i < len)) {
    var mtch = lineRE.exec(lines[i]);
    if ((mtch !== null)) {
      var x = mtch[1];
      var fnName = ((x !== (void 0)) ? (x + "()") : "global code");
      (result.push(((((fnName + "@") + mtch[2]) + ":") + mtch[3])) | 0);
    }
    i = ((1 + i) | 0);
  }
  return result;
}
function $p_jl_StackTrace$__extractOpera11__O__O($thiz, e) {
  var lineRE = $m_jl_StackTrace$StringRE$().ds("^.*line (\\d+), column (\\d+)(?: in (.+))? in (\\S+):$");
  var lines = e.stacktrace.split("\n");
  var result = [];
  var i = 0;
  var len = (lines.length | 0);
  while ((i < len)) {
    var mtch = lineRE.exec(lines[i]);
    if ((mtch !== null)) {
      var location = ((((mtch[4] + ":") + mtch[1]) + ":") + mtch[2]);
      var x$3 = mtch[2];
      var fnName0 = ((x$3 !== (void 0)) ? x$3 : "global code");
      var fnName = fnName0.replace($m_jl_StackTrace$StringRE$().ds("<anonymous function: (\\S+)>"), "$1").replace($m_jl_StackTrace$StringRE$().ds("<anonymous function>"), "{anonymous}");
      (result.push(((fnName + "@") + location)) | 0);
    }
    i = ((2 + i) | 0);
  }
  return result;
}
function $p_jl_StackTrace$__extractOther__O__O($thiz, e) {
  return [];
}
function $p_jl_StackTrace$__loop$1__I__T__T($thiz, i, encodedName$1) {
  while (true) {
    if ((i < ($p_jl_StackTrace$__compressedPrefixes__O($thiz).length | 0))) {
      var prefix = $p_jl_StackTrace$__compressedPrefixes__O($thiz)[i];
      if ((!(!encodedName$1.startsWith(prefix)))) {
        var dict = $p_jl_StackTrace$__decompressedPrefixes__O($thiz);
        return (("" + dict[prefix]) + encodedName$1.substring(prefix.length));
      } else {
        i = ((1 + i) | 0);
      }
    } else {
      return ((!(!encodedName$1.startsWith("L"))) ? encodedName$1.substring(1) : encodedName$1);
    }
  }
}
/** @constructor */
function $c_jl_StackTrace$() {
  this.jl = null;
  this.jm = null;
  this.jk = null;
  this.cL = 0;
}
$p = $c_jl_StackTrace$.prototype = new $h_O();
$p.constructor = $c_jl_StackTrace$;
/** @constructor */
function $h_jl_StackTrace$() {
}
$h_jl_StackTrace$.prototype = $p;
$p.qF = (function(jsError) {
  return $p_jl_StackTrace$__normalizedLinesToStackTrace__O__Ajl_StackTraceElement(this, $p_jl_StackTrace$__normalizeStackTraceLines__O__O(this, jsError));
});
var $d_jl_StackTrace$ = new $TypeData().i($c_jl_StackTrace$, "java.lang.StackTrace$", ({
  dF: 1
}));
var $n_jl_StackTrace$;
function $m_jl_StackTrace$() {
  if ((!$n_jl_StackTrace$)) {
    $n_jl_StackTrace$ = new $c_jl_StackTrace$();
  }
  return $n_jl_StackTrace$;
}
/** @constructor */
function $c_jl_StackTrace$StringRE$() {
}
$p = $c_jl_StackTrace$StringRE$.prototype = new $h_O();
$p.constructor = $c_jl_StackTrace$StringRE$;
/** @constructor */
function $h_jl_StackTrace$StringRE$() {
}
$h_jl_StackTrace$StringRE$.prototype = $p;
$p.ds = (function(this$) {
  return new RegExp(this$);
});
$p.cq = (function(this$, mods) {
  return new RegExp(this$, mods);
});
var $d_jl_StackTrace$StringRE$ = new $TypeData().i($c_jl_StackTrace$StringRE$, "java.lang.StackTrace$StringRE$", ({
  dG: 1
}));
var $n_jl_StackTrace$StringRE$;
function $m_jl_StackTrace$StringRE$() {
  if ((!$n_jl_StackTrace$StringRE$)) {
    $n_jl_StackTrace$StringRE$ = new $c_jl_StackTrace$StringRE$();
  }
  return $n_jl_StackTrace$StringRE$;
}
/** @constructor */
function $c_jl_System$Streams$() {
  this.lG = null;
  this.g3 = null;
  $n_jl_System$Streams$ = this;
  this.lG = new $c_jl_JSConsoleBasedPrintStream(false);
  this.g3 = new $c_jl_JSConsoleBasedPrintStream(true);
}
$p = $c_jl_System$Streams$.prototype = new $h_O();
$p.constructor = $c_jl_System$Streams$;
/** @constructor */
function $h_jl_System$Streams$() {
}
$h_jl_System$Streams$.prototype = $p;
var $d_jl_System$Streams$ = new $TypeData().i($c_jl_System$Streams$, "java.lang.System$Streams$", ({
  dJ: 1
}));
var $n_jl_System$Streams$;
function $m_jl_System$Streams$() {
  if ((!$n_jl_System$Streams$)) {
    $n_jl_System$Streams$ = new $c_jl_System$Streams$();
  }
  return $n_jl_System$Streams$;
}
function $p_jl_System$SystemProperties$__loadSystemProperties__O($thiz) {
  var result = ({});
  result["java.version"] = "1.8";
  result["java.vm.specification.version"] = "1.8";
  result["java.vm.specification.vendor"] = "Oracle Corporation";
  result["java.vm.specification.name"] = "Java Virtual Machine Specification";
  result["java.vm.name"] = "Scala.js";
  result["java.vm.version"] = "1.22.0";
  result["java.specification.version"] = "1.8";
  result["java.specification.vendor"] = "Oracle Corporation";
  result["java.specification.name"] = "Java Platform API Specification";
  result["file.separator"] = "/";
  result["path.separator"] = ":";
  result["line.separator"] = "\n";
  return result;
}
/** @constructor */
function $c_jl_System$SystemProperties$() {
  this.jn = null;
  this.lH = null;
  $n_jl_System$SystemProperties$ = this;
  this.jn = $p_jl_System$SystemProperties$__loadSystemProperties__O(this);
  this.lH = null;
}
$p = $c_jl_System$SystemProperties$.prototype = new $h_O();
$p.constructor = $c_jl_System$SystemProperties$;
/** @constructor */
function $h_jl_System$SystemProperties$() {
}
$h_jl_System$SystemProperties$.prototype = $p;
$p.kO = (function(key, default$1) {
  if ((this.jn !== null)) {
    var dict = this.jn;
    return ((!(!$m_jl_Utils$Cache$().g4.call(dict, key))) ? dict[key] : default$1);
  } else {
    return this.lH.kO(key, default$1);
  }
});
var $d_jl_System$SystemProperties$ = new $TypeData().i($c_jl_System$SystemProperties$, "java.lang.System$SystemProperties$", ({
  dK: 1
}));
var $n_jl_System$SystemProperties$;
function $m_jl_System$SystemProperties$() {
  if ((!$n_jl_System$SystemProperties$)) {
    $n_jl_System$SystemProperties$ = new $c_jl_System$SystemProperties$();
  }
  return $n_jl_System$SystemProperties$;
}
/** @constructor */
function $c_jl_ThreadLocal() {
  this.jo = false;
  this.lI = null;
  this.jo = false;
}
$p = $c_jl_ThreadLocal.prototype = new $h_O();
$p.constructor = $c_jl_ThreadLocal;
/** @constructor */
function $h_jl_ThreadLocal() {
}
$h_jl_ThreadLocal.prototype = $p;
$p.S = (function() {
  if ((!this.jo)) {
    this.hU(null);
  }
  return this.lI;
});
$p.hU = (function(o) {
  this.lI = o;
  this.jo = true;
});
var $d_jl_ThreadLocal = new $TypeData().i($c_jl_ThreadLocal, "java.lang.ThreadLocal", ({
  dM: 1
}));
/** @constructor */
function $c_jl_UnicodeData$() {
  this.lM = null;
  $n_jl_UnicodeData$ = this;
  this.lM = $constArrUDiffs_I(2891, "D4u4_@_4|7^80003f3H0004u3y3t4i3|4^3z3o8m3d4s100004]300004`3m80003w3H0004v3j<j3f2Lu3eL^30t3f10|70^23|60^G]7|9]@^:|8^7|A^7|;]8^;|9^7|L^?|@^:|9^?|7^4`4y7^8`?y4_4|7^4{7_4|23|2<^7^4{?]9]7;^G|9^7|8^>|=]13^8@`3z8003<_3H0014]8000L]3H0008t?f1@t8001Gf3H000DtKf4t3f4t23h8008D]3H0004]58x?`4t4c6|80008a3H0003{<s3f3|@x7c100004t300003f;|4^3|4^9^6|1O|4^14^4=]7|7]8^<|2O^@|7u4f;|8^6|68^60|47x3gD^8w6L^1G|<2|4^4G|8a4qGg54s3y3i8y7{3c4c3N]1Fk5o2o5k6o5k6o5k1w10b3;x@b@p7e1<mHf;|8_3{8a9g1:o3u4e;j3O|4^1:^1B]6]J`18l?j:^1|<<p3j6^Jg4i3g:]={:_6s3g>]1|8a17y<t7f4p1Ge4m3r6^1|3J^1N]1;w8b;6^19|3x1Hf17y44^13{8u3y;i3y8c4q7h2J^6]7{6_11{6_9{6_>]3w8u1Ke4b34^;w8u3e4b1;xDb30s3gGx4m7mHcO|53|4_1:]1>]8g5sJ]J]2<_1z6J^4_2{9_;{10_={6_6z7^>]9|1:^6o7n18l3i4^1N^4_5u4bOx8b7x8b2Gx4bKx4b3x<b?x9c9_;{=w:e5u:e6z6x12e1u@b7x4b>^5w8f17y8r7nHn3{3h4p3k3w:c8_1u4bGx@b7x8b2Gx4bKx4b7x4b7x4b7x8c3w6e;{5wBc5w:c6]3w>c1wLb?x4b3xLf19z5|>^2o3e1:c8_1u4b13x4b;x4b2Gx4bKx4b7x4bCx9c9_;{Aw6c8_1u6e5{3w8b3x1Lb:^5w8f18l4_3cLb6^:];w6c4_5u4bOx8b7x8b2Gx4bKx4b7x4bCx9c9_3{4_3{=w:e5u:e5{3wLc6]4_1u@b7x4b>^5w8f18p3f4cGr1:c1|3x4bGx<b;x4b?x<b7x4b3x4b7x<b7x<b;x<b1?xBe7{4_5u>e9u6e9{3w8b3xJe1u1Hf18_<nG{4_3aFc4_;{1|Ox4b;x4b2Kx4b1Ox9c9]<_=u6c9w6c:]3wNc5w4b;x8b3x8b:^5w8f17tLu3pLn3f6^4_6m3jOx4b;x4b2Kx4b17x4bCx9c9_3{4_Au6c4_5u6e7{2]3wNe5uHb7x4b:^5w8f17t4b:`1u1Bc8_5z13x4b;x4b54^7|6`;{=w6e9u6e:z7t3a@b>`2`Kw>^5w8f18_14n3fGx6c4_5u4b27x<b2Ox4b13x4b3x8bKx<c3wBe;{9w6c1w6eMuHf17t:e6m3e1@b62^1|:^Iw@w3hG|4_J]2]4o3n18l7e4Db7x4b3x4bCx4b2Ox4b3x4b1:^1|:^F]6]5|3x8bCx4a3y4cF]2]3w4f17t8b?x40b4t;y1La3y4a;g8sGj18_18n3g4s3g4s3g5l>p7zOx4b4?xBc1H_3{>]4o3k7|F^19w6c4=w4yOg4sGa4y7yDa?y7e4Db5>`7{@_3{B]6_1{:_7{5|4a18lGjJ`7{5|B^9|6`9z:`Iz>^=|1F^4_7{8_G{1|6`2^19|;{2s7b4G|4^3|D^3|8_5<s80003i3H0003{<`193x4b?x8bKx4b3x4b?x8b53x4b?x8b43x4b?x8bKx4b3x4b?x8b1Kx4b73x4b?x8b8;x8c<o100013p300014]1;r<b20t17aH^:G|8_G{8q3n2=@t3y3j24d3v39n6g<b9<p;o;xOxLb2:^6]4_3u14b2>^6_4m7e14b2:^5w1@b1Cx4b;x6c5w1@b6@^:_3{L_O{4_5{1<o;i4q<_4h6x8f17tHh17rHuGy4a?k<g3s4`17tHb4;|4^6CxLb20000F^200005|4:^1|3xDb8Gx18b3Kx6c<_?{8_9uBe7{4_E{;w@y3a<u7n17y3Gx8bCx1<b5?x@b37xHf100018_300003r<y47f2N^8_7{1w8u7j6F`3{4_3{Iw4c6_3{4_7{10_G{6]Ow8c4`17tHf17tHuKi4qGe8c1H^5|6]1>]9w66c@_1z5L^6_3{D_3{4_C{4_2]3zOx<f18lLa17g14s13y7e6c8_1z3J`3{@_7{6_3{6]5|8a17y5@^6_3{8_;{4_3{:_7u10u?j4B`O{10_7{2]3w<uCn17t<b<a17y3G|Hq7g13{L^5;|8^<tOe10c<o3k1D_3{K|A]7]E^6|4`3{7|3xD_8005@_3H007K{8001D_3H0003{80048_3H004D_4N]1F]1<xBG^10|<7^O|10^G{8^G|8_O|10^O|10^G{8^G|8_O{4^3|4^3|4^3|5_7]K|10^1G{8_10^O|10^O|10^O|C{4_7|@_4u3d4v;d;{4_7|@_4u;d?{8_7|?|4x;dO|Dw;b8_;{4_7|@_4u7b4i1<aDaHa9b6u4e8^3t4e3x10r7`Cy4i15c6w?|8^<^4z7_1<^3|3|4^17q4aCm4m17x80003v3H0003y8hHk<z80006k3H0004d18k<z6g80004a3H001Cy<w43c1Lc1D^?|4^;|1?w1Ly7b4x?b4x8c6|8^7|<^4w3b4x200007z200003eDxGb4x3b4x3b4x3b20000@x200004b7]<^4`?z4w7c7|8uDf7]<w3z4`7c4w3l@001O|3H0020]3H0020]<u7e@^4n7a@vD`Cz8`?z4`7z4`7z4`Kz4`3Kz8`7z4`3z4`3Kz11@`10v?d2?z8`Lw7c:3z4`3Gz34`4OzH`8Ca34y1;a2Dh7@n@0038]3H0038]3H0037l2HnFKz4`13z4`6Gz10`=Kz4`O0v1Fs3Hn5?zDz7`3Ly17a20`OOz@<z2G`7Ly?a40y7a108`5Oz2D`7zH`4Ka8y3Oa4y=3b60^60|;]8^9]J|=]<^;]8000D_3H0007z=]<;^4wHc?a<x6|Du?p4j7g4G{4_3{D_3{8b6OxLa4q3e1Ib7]2Gx14bKx4bKx4bKx4bKx4bKx4bKx4bKx4bKx6c3No9b>x=c6w5b6x13y4a7y4a5b6x9b7uO`Ci4q17y8a?y4a3z4`1Da7y<{N{3i48y37a4y;3a1@yJGa38y1?a@i4i<a3e4^4b5h17d8vN|5_7]4c3k13y@_8i3mDu7k;w4^4p4a7a4b:Gx8c200008r200007f8^4l3n;8p3i<^3xDb5;x4b;Gx4y7l@n17f40t4?a1@b20t3Ka4h18n3Gl10n3l1Ln3Ol18n4Kl1Ln17OfIH0t7Of2B2C|4^4>Kx<y6Ka14b4O|Hq7j11?|4q;j20a17y7x2A^5Ha7_<n5kN]8o3i4z8003?`3H000:_5|8Hb17y8oGe10x2Kf14t8c1G^8|8007G`3H0003{11]C|17`4t8d?`4y?^8|2C]A]7]A]1O]=^:{E^6|4_3{5^B|80030a3H000<{7`80003|3H0007{4`N^1|=^6|>^1|2N`7{8_2q?g3w<hHn7{4_3aHb6@p?e12e5z6:`1M{6]1w10u7n17tHc27|Hp;j4p3j:^2`17y3B^B]<o7j2N^1<_2]3u1<u3j3Cx>c<_1z5L^6_7{@_7{8_6]4m1Ce4a4b17t@u7jF^1{4^14a17yCx4b56^H_7{8_7{5w14b>^1|12^4_1u8f17t8u?j1O|4^Ht;f6`3{4_1z6:^1|6^9|:^5|F^2|>x30b7|4q7j1>`3{8_6m7j3|:a1{3w18bGx8bGx8bGx14bKx4bKx4_5<v80003f3H000?{80014_3H0004t7b@_:0`4>`7{4_7{4_6m3m3{3w8f17tHb1;D?x1@b2Kx@b63x@p7OO|HOOp1=Gx8b=7x4H_K{1@_C{Db6^1|18q3i1Cx4bCx4b3x4b7x4b7x4b=@s23b20b1==n7c1Of7Ox8b6GxLy3a40b1@r4_;g20oL{7_3eHc20o3y8`8|1O_8{7_?|<^;e4u?y5_G_<^3x4b;d4u4_3{7e@bCx4b@Kx8m3m4u<_3{<z7`4^3|3y4a7n18l8^;|7f38q4`3{4b3y4a3d38p4a3z4`4z7_4z7`7j17|4^5C|8^3Kx<bGx8bGx8bGx8b;x<w7|4_4^3{7c4y3z@`7a18m<i7a8b1?x4b37x4b2;x4b7x4b1Kx8b1Gx48b?;xDu;e@h5Cr<y13k6D^@n23l8n;a4y1Ca<y3a5Ly5Cg3w@8b3Cx<b63x1Lc4b3;r@b40c?r14b2@b3x10b3sDb4J^AwDb3Gx4u3j4?x@b10p3oCs58^50^50`9Gx8f17tH^4?|@_4?{@b4Ox10b6?x1<u3f1;|4^1K|4^K|4^7|4_1;{4_1K{4_K{4_7{8<b16Kx14b2Gx18bOx80030a3H0004]80008]3H000;y80004a3H0057y80004a3H0013y8DbGx8b3x4b5?x4b7x<b3x8b2Kx4u3pOw2Lt7lKw3Kx10h13r60b2;x4b7xDhCw2HcGr<u3j37xDu3e80b6Ox@h7w8c1Or8h5Gw6^9w6c5wFc=|?x4b;x4b3Cx8c;w@c4b13rLu13eLb3Dc8j3j3Dc;r40b10t3f3@^7w@hDjKe14b6Gx<uKj2Gx8hOw2;xDhOw27xLu?e1@hKr:0b93x6L^6;|1D_6;{LhGw4B^=w10f17t14Hh3Kr4b57x6c6k3i8b7x9<c;|3Dc17w3x10b2H^1<b@jCe2Hb28^@o?e4Hb2DcKr2@b2Kx16e3{4_1z6F^1F]4oKe@h2?{18z6|6^5|3x14c6]8_1z5F`;{@_5{8o7u4eAk1w18m3m8b33xLf17tJc9|4B^D_3{F]7w4f18l?j6`5z3x10b4<^4o7j3x16c8_1z62`;{14_2]3z@p?k@o5m3{2`17y4p3j4p;e4h2?r1<b27x4b36`;{<_7{2_3{6]2oIk1|:^1w7HbKx4b3x4b?x4b1Kx4b18p3eHb5N^4_;{F]7wDf17tJc8_5u4bOx8b7x8b2Gx4bKx4b7x4bCx4c7|6`7{4_=u:e5u:e6]3u8b3xJe1uDbF`5u8cKw<cCwA<b6F`;{10_5{6]8_2{6|<pCn18l7e4u4k6|7x3Hb62`;{H_3{4_?{8_1{7|8p3j3x10f17tDHb5N`;{=w:e?{8_1{8o2KjB^5w48b62`;{10_7{4_1{6]2o;j3x1<f17tHu1Ce2<b5>^4_3{4_7{F_4z7p3eHf17t6Hb3;x:c<_7{@_3{>]3w@f18_8j<a3fKxG4b5B`;{14_1{8o3e<@^40^40d18_13r1@bOx8b3x8bOx4b7x4b32`Eu6e5u:c6_4{9_1z6`1{4o;e14f17t8HbOx8b4N`;{=w:c8_>{7o3j6`1u3<b6^15|50^:]@_1z6^>oOk3w10b6^H_7{9|5J^1D_1{8o;j4pCe1Db93xLu17eNHb13x4b4F`3{Iw6cH_2z7pCe18f18_2;r<u7j3Gx:c2Ew6e3{L_3{8_3{5w94bKx4b7x4b4J^Ew>c1w6c5w6c:]6]2]7|6^1w10f17tHbGx4b7x4b42`Au6c5w6e7{4_2z6xLf17t16Hb2>^8_6m7eNc5|6`1z1Cx4b4:`7{Aw>e7{2_3{4o1Cn17t:Hb3x1Lh2DnO{@_23a1Du3j3C7x<Hg=Ks4uCe1<bH?x:9@b<4p7e1Db460h20s6|D^1Kw?E8b28Kx11G4b273xLb3Kx4f17t@u7j9Kx4f17tHb3Gx8cDo3e18b60^LoDa?e@q4a3a18f17t4hKr4b2CxDb2;x2F0^40^40f2Lj?e<Db9;xBc1|6`6IuNc={1Cy80a8q3i4_3w1>e5u1HbGNOx10b4JGx58b13x12LLa?y4aKy4a7y4b14;x1Lb3x3Db;x8b3x1Hb?x10b1A?x90@b=;xDb1Cx<b13xLb17x8y3g6]2o3u?mB;@c5Gw8c2Kw14y>?a7@yNGa18y4Ka8y7?i7{<s;iHeOs10s7gLs3Gg@s7Ca2Dy87g<s3a?8h2?r1@h2?r1@y:Ka14h33r@L^38^37|38^K{4_27|38^37|3|4^7|8^3|8^7|8^?|4^10^?{4_3{4_K{4_1;|38^37|7|4^?|8^O|4^K|4_37|7|4^?|4^C|4^3|<^K|4_37|38^37|38^37|38^37|38^37|38^37|38^3?{8^34u3f34t3fG|34u3f34t3fG|34u3f34t3fG|34u3f34t3fG|34u3f34t3fH|6|8f68p1OOg6Ls?g68sOg4s1Gg4s7yCe1LcCw4c1Kw4:0_18`3z2?{H_G{JFcIw6c21w:cIw6c5w6cAw8000Da3H007Gy46c1w>0b5Cx<cK{Ky8f17t@b4t3a180b3H^3w24b5@^@`17tDw3c1J0b3;|4_@`17t2LHbKx4b?x4b7x4b1Kx4bHCx8h13xKw54^48^48a>]2];{3y@f17t@u7e324h7<n3l<l3n?r9@h5Dn3l1KrH8b?x4b3;x4b7x4b3x8b3x4b17x4b?x4b3x4b3xHb3x@b3x4b3x4b3x4b;x4b7x4b3x8b3x4b3x4b3x4b3x4b3x4b7x4b3x8b?x4bKx4b?x4b?x4b3x4b17x4b23xDb;x4bCx4b23x6@v7d11Hy5?a@y<?a1@y1Ka8y1Ka4y1Ka4y4Ca18h1Dn@004<]3@0038]@000H]3@0038]@000H]3@0038]4?a70y3Ca1Dy5?a@y13aLy7a1HyGaC8yO;|D^2JOa@y23a<y1Ca<y>Ka@y;KaHy1?a@y3a1Ly1?a@y6Oa10y17aHy4Oa10y3Ga8y7a9Hy1:?a1@y1Ga8y1Ca<y13aLy5Ga4yKa10y1Ga@y13aLy13aLyB;a4y6Ka4Df17t40Hb56KOx40b@77xHbKGx8bF@7x1HbM63x<3Lb23Gx5L8bC9;xDb@;Ox2FL:4m3m3Hm;Om@0cMOw7N20o7OOGk8o7OOGk");
}
$p = $c_jl_UnicodeData$.prototype = new $h_O();
$p.constructor = $c_jl_UnicodeData$;
/** @constructor */
function $h_jl_UnicodeData$() {
}
$h_jl_UnicodeData$.prototype = $p;
$p.ri = (function(cp) {
  if (((cp >>> 0) > 1114111)) {
    return 0;
  } else {
    var shiftedCP = (cp << 7);
    var dataRanges = this.lM;
    var low = 0;
    var high = 2891;
    while ((((1 + low) | 0) !== high)) {
      var mid = ((((low + high) | 0) >>> 1) | 0);
      if (((shiftedCP >>> 0) < ((268435328 & dataRanges.a[mid]) >>> 0))) {
        high = mid;
      } else {
        low = mid;
      }
    }
    var data = dataRanges.a[low];
    var parityBit = ((1 & cp) & ((data >>> 5) | 0));
    return ((((-268435329) & data) ^ parityBit) ^ (parityBit << 1));
  }
});
$p.kT = (function(cp) {
  if (((cp >>> 0) < 161)) {
    return ((((cp - 65) | 0) >>> 0) <= 25);
  } else {
    var data = this.ri(cp);
    return (((2 & (1 << data)) | (536870912 & data)) !== 0);
  }
});
var $d_jl_UnicodeData$ = new $TypeData().i($c_jl_UnicodeData$, "java.lang.UnicodeData$", ({
  dN: 1
}));
var $n_jl_UnicodeData$;
function $m_jl_UnicodeData$() {
  if ((!$n_jl_UnicodeData$)) {
    $n_jl_UnicodeData$ = new $c_jl_UnicodeData$();
  }
  return $n_jl_UnicodeData$;
}
/** @constructor */
function $c_jl_Utils$Cache$() {
  this.g4 = null;
  $n_jl_Utils$Cache$ = this;
  this.g4 = Object.prototype.hasOwnProperty;
}
$p = $c_jl_Utils$Cache$.prototype = new $h_O();
$p.constructor = $c_jl_Utils$Cache$;
/** @constructor */
function $h_jl_Utils$Cache$() {
}
$h_jl_Utils$Cache$.prototype = $p;
var $d_jl_Utils$Cache$ = new $TypeData().i($c_jl_Utils$Cache$, "java.lang.Utils$Cache$", ({
  dP: 1
}));
var $n_jl_Utils$Cache$;
function $m_jl_Utils$Cache$() {
  if ((!$n_jl_Utils$Cache$)) {
    $n_jl_Utils$Cache$ = new $c_jl_Utils$Cache$();
  }
  return $n_jl_Utils$Cache$;
}
function $f_jl_Void__equals__O__Z($thiz, that) {
  return ($thiz === that);
}
function $f_jl_Void__hashCode__I($thiz) {
  return 0;
}
function $f_jl_Void__toString__T($thiz) {
  return "undefined";
}
function $isArrayOf_jl_Void(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bB)));
}
var $d_jl_Void = new $TypeData().i(0, "java.lang.Void", ({
  bB: 1
}), ((x) => (x === (void 0))));
function $p_jl_reflect_Array$__mismatch__O__E($thiz, array) {
  throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), "argument type mismatch");
}
/** @constructor */
function $c_jl_reflect_Array$() {
}
$p = $c_jl_reflect_Array$.prototype = new $h_O();
$p.constructor = $c_jl_reflect_Array$;
/** @constructor */
function $h_jl_reflect_Array$() {
}
$h_jl_reflect_Array$.prototype = $p;
$p.b8 = (function(array) {
  if ((array instanceof $ac_O)) {
    return array.a.length;
  } else if ((array instanceof $ac_Z)) {
    return array.a.length;
  } else if ((array instanceof $ac_C)) {
    return array.a.length;
  } else if ((array instanceof $ac_B)) {
    return array.a.length;
  } else if ((array instanceof $ac_S)) {
    return array.a.length;
  } else if ((array instanceof $ac_I)) {
    return array.a.length;
  } else if ((array instanceof $ac_J)) {
    return ((array.a.length >>> 1) | 0);
  } else if ((array instanceof $ac_F)) {
    return array.a.length;
  } else {
    if ((!(array instanceof $ac_D))) {
      $p_jl_reflect_Array$__mismatch__O__E(this, array);
    }
    return array.a.length;
  }
});
var $d_jl_reflect_Array$ = new $TypeData().i($c_jl_reflect_Array$, "java.lang.reflect.Array$", ({
  dQ: 1
}));
var $n_jl_reflect_Array$;
function $m_jl_reflect_Array$() {
  if ((!$n_jl_reflect_Array$)) {
    $n_jl_reflect_Array$ = new $c_jl_reflect_Array$();
  }
  return $n_jl_reflect_Array$;
}
/** @constructor */
function $c_ju_Arrays$() {
}
$p = $c_ju_Arrays$.prototype = new $h_O();
$p.constructor = $c_ju_Arrays$;
/** @constructor */
function $h_ju_Arrays$() {
}
$h_ju_Arrays$.prototype = $p;
$p.oS = (function(a) {
  var comparator = $m_ju_internal_GenericArrayOps$IntArrayOps$();
  var ops = $m_ju_internal_GenericArrayOps$IntArrayOps$();
  var end = a.a.length;
  if ((end > 16)) {
    this.cp(a, new $ac_I(a.a.length), 0, end, comparator, ops);
  } else {
    this.cG(a, 0, end, comparator, ops);
  }
});
$p.s7 = (function(a, fromIndex, toIndex) {
  var comparator = $m_ju_internal_GenericArrayOps$IntArrayOps$();
  var ops = $m_ju_internal_GenericArrayOps$IntArrayOps$();
  if ((fromIndex > toIndex)) {
    throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), (((("fromIndex(" + fromIndex) + ") > toIndex(") + toIndex) + ")"));
  }
  if ((((toIndex - fromIndex) | 0) > 16)) {
    this.cp(a, new $ac_I(a.a.length), fromIndex, toIndex, comparator, ops);
  } else {
    this.cG(a, fromIndex, toIndex, comparator, ops);
  }
});
$p.oT = (function(a) {
  var comparator = $m_ju_internal_GenericArrayOps$LongArrayOps$();
  var ops = $m_ju_internal_GenericArrayOps$LongArrayOps$();
  var end = ((a.a.length >>> 1) | 0);
  if ((end > 16)) {
    this.cp(a, new $ac_J(((a.a.length >>> 1) | 0)), 0, end, comparator, ops);
  } else {
    this.cG(a, 0, end, comparator, ops);
  }
});
$p.s8 = (function(a, fromIndex, toIndex) {
  var comparator = $m_ju_internal_GenericArrayOps$LongArrayOps$();
  var ops = $m_ju_internal_GenericArrayOps$LongArrayOps$();
  if ((fromIndex > toIndex)) {
    throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), (((("fromIndex(" + fromIndex) + ") > toIndex(") + toIndex) + ")"));
  }
  if ((((toIndex - fromIndex) | 0) > 16)) {
    this.cp(a, new $ac_J(((a.a.length >>> 1) | 0)), fromIndex, toIndex, comparator, ops);
  } else {
    this.cG(a, fromIndex, toIndex, comparator, ops);
  }
});
$p.oU = (function(a) {
  var comparator = $m_ju_internal_GenericArrayOps$ShortArrayOps$();
  var ops = $m_ju_internal_GenericArrayOps$ShortArrayOps$();
  var end = a.a.length;
  if ((end > 16)) {
    this.cp(a, new $ac_S(a.a.length), 0, end, comparator, ops);
  } else {
    this.cG(a, 0, end, comparator, ops);
  }
});
$p.s9 = (function(a, fromIndex, toIndex) {
  var comparator = $m_ju_internal_GenericArrayOps$ShortArrayOps$();
  var ops = $m_ju_internal_GenericArrayOps$ShortArrayOps$();
  if ((fromIndex > toIndex)) {
    throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), (((("fromIndex(" + fromIndex) + ") > toIndex(") + toIndex) + ")"));
  }
  if ((((toIndex - fromIndex) | 0) > 16)) {
    this.cp(a, new $ac_S(a.a.length), fromIndex, toIndex, comparator, ops);
  } else {
    this.cG(a, fromIndex, toIndex, comparator, ops);
  }
});
$p.oR = (function(a) {
  var comparator = $m_ju_internal_GenericArrayOps$CharArrayOps$();
  var ops = $m_ju_internal_GenericArrayOps$CharArrayOps$();
  var end = a.a.length;
  if ((end > 16)) {
    this.cp(a, new $ac_C(a.a.length), 0, end, comparator, ops);
  } else {
    this.cG(a, 0, end, comparator, ops);
  }
});
$p.s6 = (function(a, fromIndex, toIndex) {
  var comparator = $m_ju_internal_GenericArrayOps$CharArrayOps$();
  var ops = $m_ju_internal_GenericArrayOps$CharArrayOps$();
  if ((fromIndex > toIndex)) {
    throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), (((("fromIndex(" + fromIndex) + ") > toIndex(") + toIndex) + ")"));
  }
  if ((((toIndex - fromIndex) | 0) > 16)) {
    this.cp(a, new $ac_C(a.a.length), fromIndex, toIndex, comparator, ops);
  } else {
    this.cG(a, fromIndex, toIndex, comparator, ops);
  }
});
$p.oQ = (function(a) {
  var comparator = $m_ju_internal_GenericArrayOps$ByteArrayOps$();
  var ops = $m_ju_internal_GenericArrayOps$ByteArrayOps$();
  var end = a.a.length;
  if ((end > 16)) {
    this.cp(a, new $ac_B(a.a.length), 0, end, comparator, ops);
  } else {
    this.cG(a, 0, end, comparator, ops);
  }
});
$p.s5 = (function(a, fromIndex, toIndex) {
  var comparator = $m_ju_internal_GenericArrayOps$ByteArrayOps$();
  var ops = $m_ju_internal_GenericArrayOps$ByteArrayOps$();
  if ((fromIndex > toIndex)) {
    throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), (((("fromIndex(" + fromIndex) + ") > toIndex(") + toIndex) + ")"));
  }
  if ((((toIndex - fromIndex) | 0) > 16)) {
    this.cp(a, new $ac_B(a.a.length), fromIndex, toIndex, comparator, ops);
  } else {
    this.cG(a, fromIndex, toIndex, comparator, ops);
  }
});
$p.gz = (function(array, comparator) {
  var comparator$1 = ((comparator === null) ? $m_ju_Arrays$NaturalComparator$() : comparator);
  var ops = $m_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$();
  var end = array.a.length;
  if ((end > 16)) {
    this.cp(array, $objectGetClass(array).J.Q().J.U(array.a.length), 0, end, comparator$1, ops);
  } else {
    this.cG(array, 0, end, comparator$1, ops);
  }
});
$p.sa = (function(array, fromIndex, toIndex, comparator) {
  var comparator$1 = ((comparator === null) ? $m_ju_Arrays$NaturalComparator$() : comparator);
  var ops = $m_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$();
  if ((fromIndex > toIndex)) {
    throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), (((("fromIndex(" + fromIndex) + ") > toIndex(") + toIndex) + ")"));
  }
  if ((((toIndex - fromIndex) | 0) > 16)) {
    this.cp(array, $objectGetClass(array).J.Q().J.U(array.a.length), fromIndex, toIndex, comparator$1, ops);
  } else {
    this.cG(array, fromIndex, toIndex, comparator$1, ops);
  }
});
$p.cp = (function(a, temp, start, end, comparator, ops) {
  var length = ((end - start) | 0);
  if ((length > 16)) {
    var middle = ((start + (((length + ((length >>> 31) | 0)) | 0) >> 1)) | 0);
    this.cp(a, temp, start, middle, comparator, ops);
    this.cp(a, temp, middle, end, comparator, ops);
    var outIndex = start;
    var leftInIndex = start;
    var rightInIndex = middle;
    while ((outIndex < end)) {
      if (((leftInIndex < middle) && ((rightInIndex >= end) || (comparator.ax(ops.c0(a, leftInIndex), ops.c0(a, rightInIndex)) <= 0)))) {
        ops.dt(temp, outIndex, ops.c0(a, leftInIndex));
        leftInIndex = ((1 + leftInIndex) | 0);
      } else {
        ops.dt(temp, outIndex, ops.c0(a, rightInIndex));
        rightInIndex = ((1 + rightInIndex) | 0);
      }
      outIndex = ((1 + outIndex) | 0);
    }
    temp.s(start, a, start, length);
  } else {
    this.cG(a, start, end, comparator, ops);
  }
});
$p.cG = (function(a, start, end, comparator, ops) {
  var n = ((end - start) | 0);
  if ((n >= 2)) {
    var aStart = ops.c0(a, start);
    var aStartPlusOne = ops.c0(a, ((1 + start) | 0));
    if ((comparator.ax(aStart, aStartPlusOne) > 0)) {
      ops.dt(a, start, aStartPlusOne);
      ops.dt(a, ((1 + start) | 0), aStart);
    }
    var m = 2;
    while ((m < n)) {
      var next = ops.c0(a, ((start + m) | 0));
      if ((comparator.ax(next, ops.c0(a, ((((start + m) | 0) - 1) | 0))) < 0)) {
        var iA = start;
        var iB = ((((start + m) | 0) - 1) | 0);
        while ((((iB - iA) | 0) > 1)) {
          var ix = ((((iA + iB) | 0) >>> 1) | 0);
          if ((comparator.ax(next, ops.c0(a, ix)) < 0)) {
            iB = ix;
          } else {
            iA = ix;
          }
        }
        var ix$2 = ((iA + ((comparator.ax(next, ops.c0(a, iA)) >= 0) | 0)) | 0);
        var i = ((start + m) | 0);
        while ((i > ix$2)) {
          ops.dt(a, i, ops.c0(a, ((i - 1) | 0)));
          i = ((i - 1) | 0);
        }
        ops.dt(a, ix$2, next);
      }
      m = ((1 + m) | 0);
    }
  }
});
$p.q7 = (function(a, key) {
  var startIndex = 0;
  var endIndex = a.a.length;
  while (true) {
    if ((startIndex === endIndex)) {
      return (~startIndex);
    } else {
      var mid = ((((startIndex + endIndex) | 0) >>> 1) | 0);
      var elem = a.a[mid];
      var cmp = ((key === elem) ? 0 : ((key < elem) ? (-1) : 1));
      if ((cmp < 0)) {
        endIndex = mid;
        continue;
      }
      if ((cmp !== 0)) {
        startIndex = ((1 + mid) | 0);
        continue;
      }
      return mid;
    }
  }
});
$p.od = (function(a, b) {
  if ((a === b)) {
    return true;
  }
  if (((a === null) || (b === null))) {
    return false;
  }
  var len = ((a.a.length >>> 1) | 0);
  if ((((b.a.length >>> 1) | 0) !== len)) {
    return false;
  }
  var i = 0;
  while ((i !== len)) {
    var i$1 = i;
    var $x_1 = a.a;
    var $x_2 = (i$1 << 1);
    var a$1_$_lo = $x_1[$x_2];
    var a$1_$_hi = $x_1[(($x_2 + 1) | 0)];
    var i$2 = i;
    var $x_3 = b.a;
    var $x_4 = (i$2 << 1);
    var b$1_$_lo = $x_3[$x_4];
    var b$1_$_hi = $x_3[(($x_4 + 1) | 0)];
    if ((!(((a$1_$_lo ^ b$1_$_lo) | (a$1_$_hi ^ b$1_$_hi)) === 0))) {
      return false;
    }
    i = ((1 + i) | 0);
  }
  return true;
});
$p.iS = (function(a, b) {
  if ((a === b)) {
    return true;
  }
  if (((a === null) || (b === null))) {
    return false;
  }
  var len = a.a.length;
  if ((b.a.length !== len)) {
    return false;
  }
  var i = 0;
  while ((i !== len)) {
    var i$1 = i;
    var $x_1 = a.a[i$1];
    var i$2 = i;
    if ((!($x_1 === b.a[i$2]))) {
      return false;
    }
    i = ((1 + i) | 0);
  }
  return true;
});
$p.oe = (function(a, b) {
  if ((a === b)) {
    return true;
  }
  if (((a === null) || (b === null))) {
    return false;
  }
  var len = a.a.length;
  if ((b.a.length !== len)) {
    return false;
  }
  var i = 0;
  while ((i !== len)) {
    var i$1 = i;
    var $x_1 = a.a[i$1];
    var i$2 = i;
    if ((!($x_1 === b.a[i$2]))) {
      return false;
    }
    i = ((1 + i) | 0);
  }
  return true;
});
$p.oc = (function(a, b) {
  if ((a === b)) {
    return true;
  }
  if (((a === null) || (b === null))) {
    return false;
  }
  var len = a.a.length;
  if ((b.a.length !== len)) {
    return false;
  }
  var i = 0;
  while ((i !== len)) {
    var i$1 = i;
    var $x_1 = a.a[i$1];
    var i$2 = i;
    if ((!($x_1 === b.a[i$2]))) {
      return false;
    }
    i = ((1 + i) | 0);
  }
  return true;
});
$p.ob = (function(a, b) {
  if ((a === b)) {
    return true;
  }
  if (((a === null) || (b === null))) {
    return false;
  }
  var len = a.a.length;
  if ((b.a.length !== len)) {
    return false;
  }
  var i = 0;
  while ((i !== len)) {
    var i$1 = i;
    var $x_1 = a.a[i$1];
    var i$2 = i;
    if ((!($x_1 === b.a[i$2]))) {
      return false;
    }
    i = ((1 + i) | 0);
  }
  return true;
});
$p.of = (function(a, b) {
  if ((a === b)) {
    return true;
  }
  if (((a === null) || (b === null))) {
    return false;
  }
  var len = a.a.length;
  if ((b.a.length !== len)) {
    return false;
  }
  var i = 0;
  while ((i !== len)) {
    var i$1 = i;
    var $x_1 = a.a[i$1];
    var i$2 = i;
    if ((!($x_1 === b.a[i$2]))) {
      return false;
    }
    i = ((1 + i) | 0);
  }
  return true;
});
$p.qI = (function(a, value) {
  var toIndex = a.a.length;
  var i = 0;
  while ((i !== toIndex)) {
    var i$1 = i;
    a.a[i$1] = value;
    i = ((1 + i) | 0);
  }
});
$p.a9 = (function(original, newLength) {
  var b = original.a.length;
  var copyLength = ((newLength < b) ? newLength : b);
  var ret = $objectGetClass(original).J.Q().J.U(newLength);
  original.s(0, ret, 0, copyLength);
  return ret;
});
$p.kv = (function(original, newLength, newType) {
  var b = original.a.length;
  var copyLength = ((newLength < b) ? newLength : b);
  var ret = newType.J.Q().J.U(newLength);
  original.s(0, ret, 0, copyLength);
  return ret;
});
$p.o2 = (function(original, newLength) {
  var b = original.a.length;
  var copyLength = ((newLength < b) ? newLength : b);
  var ret = new $ac_B(newLength);
  original.s(0, ret, 0, copyLength);
  return ret;
});
$p.o6 = (function(original, newLength) {
  var b = original.a.length;
  var copyLength = ((newLength < b) ? newLength : b);
  var ret = new $ac_S(newLength);
  original.s(0, ret, 0, copyLength);
  return ret;
});
$p.o4 = (function(original, newLength) {
  var b = original.a.length;
  var copyLength = ((newLength < b) ? newLength : b);
  var ret = new $ac_I(newLength);
  original.s(0, ret, 0, copyLength);
  return ret;
});
$p.o5 = (function(original, newLength) {
  var b = ((original.a.length >>> 1) | 0);
  var copyLength = ((newLength < b) ? newLength : b);
  var ret = new $ac_J(newLength);
  original.s(0, ret, 0, copyLength);
  return ret;
});
$p.o3 = (function(original, newLength) {
  var b = original.a.length;
  var copyLength = ((newLength < b) ? newLength : b);
  var ret = new $ac_C(newLength);
  original.s(0, ret, 0, copyLength);
  return ret;
});
$p.qo = (function(original, newLength) {
  var b = original.a.length;
  var copyLength = ((newLength < b) ? newLength : b);
  var ret = new $ac_F(newLength);
  original.s(0, ret, 0, copyLength);
  return ret;
});
$p.qn = (function(original, newLength) {
  var b = original.a.length;
  var copyLength = ((newLength < b) ? newLength : b);
  var ret = new $ac_D(newLength);
  original.s(0, ret, 0, copyLength);
  return ret;
});
$p.o7 = (function(original, newLength) {
  var b = original.a.length;
  var copyLength = ((newLength < b) ? newLength : b);
  var ret = new $ac_Z(newLength);
  original.s(0, ret, 0, copyLength);
  return ret;
});
$p.aN = (function(original, from, to) {
  if ((from > to)) {
    throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), ((from + " > ") + to));
  }
  var len = original.a.length;
  var retLength = ((to - from) | 0);
  var b = ((len - from) | 0);
  var copyLength = ((retLength < b) ? retLength : b);
  var ret = $objectGetClass(original).J.Q().J.U(retLength);
  original.s(from, ret, 0, copyLength);
  return ret;
});
var $d_ju_Arrays$ = new $TypeData().i($c_ju_Arrays$, "java.util.Arrays$", ({
  dT: 1
}));
var $n_ju_Arrays$;
function $m_ju_Arrays$() {
  if ((!$n_ju_Arrays$)) {
    $n_ju_Arrays$ = new $c_ju_Arrays$();
  }
  return $n_ju_Arrays$;
}
function $is_ju_Formattable(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.bC)));
}
function $isArrayOf_ju_Formattable(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bC)));
}
/** @constructor */
function $c_ju_Formatter$() {
  this.lS = null;
  this.lR = null;
  $n_ju_Formatter$ = this;
  this.lS = new RegExp("(?:(\\d+)\\$)?([-#+ 0,\\(<]*)(\\d+)?(?:\\.(\\d+))?[%A-Za-z]", "g");
  this.lR = $constArrSVals_I(26, "3]3{0C{0H_1]8]_3{||||||0I]|||3y||||0A]||");
}
$p = $c_ju_Formatter$.prototype = new $h_O();
$p.constructor = $c_ju_Formatter$;
/** @constructor */
function $h_ju_Formatter$() {
}
$h_ju_Formatter$.prototype = $p;
$p.kW = (function(count) {
  if ((count <= 20)) {
    return "00000000000000000000".substring(0, count);
  } else {
    var result = "";
    var remaining = count;
    while ((remaining > 20)) {
      result = (result + "00000000000000000000");
      remaining = ((remaining - 20) | 0);
    }
    var $x_1 = result;
    var endIndex = remaining;
    return (("" + $x_1) + "00000000000000000000".substring(0, endIndex));
  }
});
$p.rk = (function(x) {
  if ((x === 0.0)) {
    return new $c_ju_Formatter$Decimal(((1.0 / x) < 0.0), "0", 0);
  } else {
    var negative$2 = (x < 0.0);
    var d = (negative$2 ? (-x) : x);
    var s = ("" + d);
    var ePos = $f_T__indexOf__I__I(s, 101);
    if ((ePos < 0)) {
      var e = 0;
    } else {
      var $x_2 = parseInt;
      var beginIndex = ((1 + ePos) | 0);
      var $x_1 = $x_2(s.substring(beginIndex));
      var e = ($x_1 | 0);
    }
    var significandEnd = ((ePos < 0) ? s.length : ePos);
    var dotPos = $f_T__indexOf__I__I(s, 46);
    if ((dotPos < 0)) {
      return new $c_ju_Formatter$Decimal(negative$2, s.substring(0, significandEnd), ((-e) | 0));
    } else {
      var $x_3 = s.substring(0, dotPos);
      var beginIndex$1 = ((1 + dotPos) | 0);
      var digits = (("" + $x_3) + s.substring(beginIndex$1, significandEnd));
      var digitsLen = digits.length;
      var i = 0;
      while (((i < digitsLen) && (digits.charCodeAt(i) === 48))) {
        i = ((1 + i) | 0);
      }
      var beginIndex$2 = i;
      return new $c_ju_Formatter$Decimal(negative$2, digits.substring(beginIndex$2), ((((-e) | 0) + ((significandEnd - ((1 + dotPos) | 0)) | 0)) | 0));
    }
  }
});
$p.rj = (function(x) {
  var unscaledValueWithSign = x.sE().t();
  if ((unscaledValueWithSign === "0")) {
    return new $c_ju_Formatter$Decimal(false, "0", 0);
  } else {
    var negative = (unscaledValueWithSign.charCodeAt(0) === 45);
    return new $c_ju_Formatter$Decimal(negative, (negative ? unscaledValueWithSign.substring(1) : unscaledValueWithSign), x.sz());
  }
});
var $d_ju_Formatter$ = new $TypeData().i($c_ju_Formatter$, "java.util.Formatter$", ({
  dZ: 1
}));
var $n_ju_Formatter$;
function $m_ju_Formatter$() {
  if ((!$n_ju_Formatter$)) {
    $n_ju_Formatter$ = new $c_ju_Formatter$();
  }
  return $n_ju_Formatter$;
}
function $p_ju_Formatter$Decimal__roundAtPos__I__ju_Formatter$Decimal($thiz, roundingPos) {
  var digits = $thiz.e1;
  var digitsLen = digits.length;
  if ((roundingPos < 0)) {
    return new $c_ju_Formatter$Decimal($thiz.e0, "0", 0);
  } else if ((roundingPos >= digitsLen)) {
    return $thiz;
  } else if ((digits.charCodeAt(roundingPos) < 53)) {
    return ((roundingPos === 0) ? new $c_ju_Formatter$Decimal($thiz.e0, "0", 0) : new $c_ju_Formatter$Decimal($thiz.e0, digits.substring(0, roundingPos), (($thiz.dy - ((digitsLen - roundingPos) | 0)) | 0)));
  } else {
    var lastNonNinePos = ((roundingPos - 1) | 0);
    while (((lastNonNinePos >= 0) && (digits.charCodeAt(lastNonNinePos) === 57))) {
      lastNonNinePos = ((lastNonNinePos - 1) | 0);
    }
    if ((lastNonNinePos < 0)) {
      var newUnscaledValue = "1";
    } else {
      var endIndex = lastNonNinePos;
      var newUnscaledValue = (digits.substring(0, endIndex) + $cToS((65535 & ((1 + digits.charCodeAt(lastNonNinePos)) | 0))));
    }
    var pos = ((1 + lastNonNinePos) | 0);
    var newScale = (($thiz.dy - ((digitsLen - pos) | 0)) | 0);
    return new $c_ju_Formatter$Decimal($thiz.e0, newUnscaledValue, newScale);
  }
}
/** @constructor */
function $c_ju_Formatter$Decimal(negative, unscaledValue, scale) {
  this.e0 = false;
  this.e1 = null;
  this.dy = 0;
  this.e0 = negative;
  this.e1 = unscaledValue;
  this.dy = scale;
}
$p = $c_ju_Formatter$Decimal.prototype = new $h_O();
$p.constructor = $c_ju_Formatter$Decimal;
/** @constructor */
function $h_ju_Formatter$Decimal() {
}
$h_ju_Formatter$Decimal.prototype = $p;
$p.ou = (function() {
  return (this.e1 === "0");
});
$p.oK = (function(precision) {
  $m_ju_Formatter$();
  if ((!(precision > 0))) {
    throw new $c_jl_AssertionError("Decimal.round() called with non-positive precision");
  }
  return $p_ju_Formatter$Decimal__roundAtPos__I__ju_Formatter$Decimal(this, precision);
});
$p.s2 = (function(newScale) {
  var roundingPos = ((((this.e1.length + newScale) | 0) - this.dy) | 0);
  var rounded = $p_ju_Formatter$Decimal__roundAtPos__I__ju_Formatter$Decimal(this, roundingPos);
  $m_ju_Formatter$();
  if ((!(rounded.ou() || (rounded.dy <= newScale)))) {
    throw new $c_jl_AssertionError("roundAtPos returned a non-zero value with a scale too large");
  }
  return ((rounded.ou() || (rounded.dy === newScale)) ? rounded : new $c_ju_Formatter$Decimal(this.e0, (("" + rounded.e1) + $m_ju_Formatter$().kW(((newScale - rounded.dy) | 0))), newScale));
});
$p.t = (function() {
  return (((((("Decimal(" + this.e0) + ", ") + this.e1) + ", ") + this.dy) + ")");
});
var $d_ju_Formatter$Decimal = new $TypeData().i($c_ju_Formatter$Decimal, "java.util.Formatter$Decimal", ({
  e0: 1
}));
/** @constructor */
function $c_ju_Formatter$LocaleInfo() {
}
$p = $c_ju_Formatter$LocaleInfo.prototype = new $h_O();
$p.constructor = $c_ju_Formatter$LocaleInfo;
/** @constructor */
function $h_ju_Formatter$LocaleInfo() {
}
$h_ju_Formatter$LocaleInfo.prototype = $p;
function $p_ju_regex_PatternCompiler__parseError__T__E($thiz, desc) {
  throw new $c_ju_regex_PatternSyntaxException(desc, $thiz.be, $thiz.b);
}
function $p_ju_regex_PatternCompiler__processLeadingEmbeddedFlags__V($thiz) {
  var m = $m_ju_regex_PatternCompiler$().ml.exec($thiz.be);
  if ((m !== null)) {
    var x = m[1];
    if ((x !== (void 0))) {
      var end = x.length;
      var i = 0;
      while ((i < end)) {
        var value = i;
        $thiz.a4 = ($thiz.a4 | $m_ju_regex_PatternCompiler$().ow(x.charCodeAt(value)));
        i = ((1 + i) | 0);
      }
    }
    if (((256 & $thiz.a4) !== 0)) {
      $thiz.a4 = (64 | $thiz.a4);
    }
    var x$1 = m[2];
    if ((x$1 !== (void 0))) {
      var end$1 = x$1.length;
      var i$1 = 0;
      while ((i$1 < end$1)) {
        var value$1 = i$1;
        $thiz.a4 = ($thiz.a4 & (~$m_ju_regex_PatternCompiler$().ow(x$1.charCodeAt(value$1))));
        i$1 = ((1 + i$1) | 0);
      }
    }
    $thiz.b = (($thiz.b + m[0].length) | 0);
  }
}
function $p_ju_regex_PatternCompiler__literal__T__T($thiz, s) {
  var result = "";
  var len = s.length;
  var i = 0;
  while ((i !== len)) {
    var cp = $f_T__codePointAt__I__I(s, i);
    result = (("" + result) + $p_ju_regex_PatternCompiler__literal__I__T($thiz, cp));
    i = ((i + ((cp >= 65536) ? 2 : 1)) | 0);
  }
  return result;
}
function $p_ju_regex_PatternCompiler__literal__I__T($thiz, cp) {
  var s = $m_ju_regex_PatternCompiler$().fG(cp);
  if ((cp < 128)) {
    switch (cp) {
      case 94:
      case 36:
      case 92:
      case 46:
      case 42:
      case 43:
      case 63:
      case 40:
      case 41:
      case 91:
      case 93:
      case 123:
      case 125:
      case 124: {
        return ("\\" + s);
        break;
      }
      default: {
        return (((66 & $thiz.a4) !== 2) ? s : (((((cp - 65) | 0) >>> 0) <= 25) ? ((("[" + s) + $m_ju_regex_PatternCompiler$().fG(((32 + cp) | 0))) + "]") : (((((cp - 97) | 0) >>> 0) <= 25) ? ((("[" + $m_ju_regex_PatternCompiler$().fG(((cp - 32) | 0))) + s) + "]") : s)));
      }
    }
  } else {
    return ((((-1024) & cp) === 56320) ? (("(?:" + s) + ")") : s);
  }
}
function $p_ju_regex_PatternCompiler__skipComments__V($thiz) {
  var pattern = $thiz.be;
  var len = pattern.length;
  while (true) {
    if (($thiz.b !== len)) {
      switch (pattern.charCodeAt($thiz.b)) {
        case 32:
        case 9:
        case 10:
        case 11:
        case 12:
        case 13: {
          $thiz.b = ((1 + $thiz.b) | 0);
          continue;
          break;
        }
        case 35: {
          $thiz.kY();
          continue;
          break;
        }
      }
    }
    break;
  }
}
function $p_ju_regex_PatternCompiler__compileRepeater__I__T__T($thiz, compiledGroupCountBeforeThisToken, compiledToken) {
  var pattern = $thiz.be;
  var len = pattern.length;
  var startOfRepeater = $thiz.b;
  var repeaterDispatchChar = ((startOfRepeater === len) ? 46 : pattern.charCodeAt(startOfRepeater));
  if (((((repeaterDispatchChar === 42) || (repeaterDispatchChar === 63)) || (repeaterDispatchChar === 43)) || (repeaterDispatchChar === 123))) {
    switch (compiledToken.charCodeAt(0)) {
      case 94:
      case 36: {
        var isTokenAnAssertion = true;
        break;
      }
      case 40: {
        var isTokenAnAssertion = ((compiledToken.charCodeAt(1) === 63) && (compiledToken.charCodeAt(2) !== 58));
        break;
      }
      case 92: {
        var c = compiledToken.charCodeAt(1);
        var isTokenAnAssertion = ((c === 66) || (c === 98));
        break;
      }
      default: {
        var isTokenAnAssertion = false;
      }
    }
    var wrappedToken = (isTokenAnAssertion ? (("(?:" + compiledToken) + ")") : compiledToken);
    var baseRepeater = $p_ju_regex_PatternCompiler__parseBaseRepeater__C__T($thiz, repeaterDispatchChar);
    if (($thiz.b !== len)) {
      switch (pattern.charCodeAt($thiz.b)) {
        case 43: {
          $thiz.b = ((1 + $thiz.b) | 0);
          return $p_ju_regex_PatternCompiler__buildPossessiveQuantifier__I__T__T__T($thiz, compiledGroupCountBeforeThisToken, wrappedToken, baseRepeater);
          break;
        }
        case 63: {
          $thiz.b = ((1 + $thiz.b) | 0);
          return ((("" + wrappedToken) + baseRepeater) + "?");
          break;
        }
        default: {
          return (("" + wrappedToken) + baseRepeater);
        }
      }
    } else {
      return (("" + wrappedToken) + baseRepeater);
    }
  } else {
    return compiledToken;
  }
}
function $p_ju_regex_PatternCompiler__parseBaseRepeater__C__T($thiz, repeaterDispatchChar) {
  var pattern = $thiz.be;
  var startOfRepeater = $thiz.b;
  $thiz.b = ((1 + $thiz.b) | 0);
  if ((repeaterDispatchChar === 123)) {
    var len = pattern.length;
    if ((($thiz.b === len) || (!((((pattern.charCodeAt($thiz.b) - 48) | 0) >>> 0) <= 9)))) {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Illegal repetition");
    }
    while ((($thiz.b !== len) && ((((pattern.charCodeAt($thiz.b) - 48) | 0) >>> 0) <= 9))) {
      $thiz.b = ((1 + $thiz.b) | 0);
    }
    if (($thiz.b === len)) {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Illegal repetition");
    }
    if ((pattern.charCodeAt($thiz.b) === 44)) {
      $thiz.b = ((1 + $thiz.b) | 0);
      while ((($thiz.b !== len) && ((((pattern.charCodeAt($thiz.b) - 48) | 0) >>> 0) <= 9))) {
        $thiz.b = ((1 + $thiz.b) | 0);
      }
    }
    if ((($thiz.b === len) || (pattern.charCodeAt($thiz.b) !== 125))) {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Illegal repetition");
    }
    $thiz.b = ((1 + $thiz.b) | 0);
  }
  return pattern.substring(startOfRepeater, $thiz.b);
}
function $p_ju_regex_PatternCompiler__buildPossessiveQuantifier__I__T__T__T($thiz, compiledGroupCountBeforeThisToken, compiledToken, baseRepeater) {
  var end = ($thiz.cu.length | 0);
  var i = 0;
  while ((i < end)) {
    var value = i;
    var mapped = ($thiz.cu[value] | 0);
    if ((mapped > compiledGroupCountBeforeThisToken)) {
      $thiz.cu[value] = ((1 + mapped) | 0);
    }
    i = ((1 + i) | 0);
  }
  var amendedToken = compiledToken.replace($m_ju_regex_PatternCompiler$().mm, ((arg1$2, arg2$2, arg3$2) => {
    var num = arg2$2.length;
    var t = ((num >>> 31) | 0);
    if (((((1 & ((num + t) | 0)) - t) | 0) === 0)) {
      return arg1$2;
    } else {
      var groupNumber = (parseInt(arg3$2, 10) | 0);
      return ((groupNumber > compiledGroupCountBeforeThisToken) ? (("" + arg2$2) + ((1 + groupNumber) | 0)) : arg1$2);
    }
  }));
  $thiz.ct = ((1 + $thiz.ct) | 0);
  var myGroupNumber = ((1 + compiledGroupCountBeforeThisToken) | 0);
  return ((((("(?:(?=(" + amendedToken) + baseRepeater) + "))\\") + myGroupNumber) + ")");
}
function $p_ju_regex_PatternCompiler__compileEscape__T($thiz) {
  var pattern = $thiz.be;
  var len = pattern.length;
  if ((((1 + $thiz.b) | 0) === len)) {
    $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "\\ at end of pattern");
  }
  $thiz.b = ((1 + $thiz.b) | 0);
  var index = $thiz.b;
  var dispatchChar = pattern.charCodeAt(index);
  switch (dispatchChar) {
    case 100:
    case 68:
    case 104:
    case 72:
    case 115:
    case 83:
    case 118:
    case 86:
    case 119:
    case 87:
    case 112:
    case 80: {
      var cls = $p_ju_regex_PatternCompiler__parsePredefinedCharacterClass__C__ju_regex_PatternCompiler$CompiledCharClass($thiz, dispatchChar);
      var x1$2 = cls.i8;
      switch (x1$2) {
        case 0: {
          return (("\\p{" + cls.dz) + "}");
          break;
        }
        case 1: {
          return (("\\P{" + cls.dz) + "}");
          break;
        }
        case 2: {
          return (("[" + cls.dz) + "]");
          break;
        }
        case 3: {
          return $m_ju_regex_PatternCompiler$().iZ(cls.dz);
          break;
        }
        default: {
          throw new $c_jl_AssertionError(x1$2);
        }
      }
      break;
    }
    case 98: {
      if ((pattern.substring($thiz.b, ((4 + $thiz.b) | 0)) === "b{g}")) {
        $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "\\b{g} is not supported");
      } else if (((320 & $thiz.a4) !== 0)) {
        $thiz.gv("\\b with UNICODE_CASE", "2018");
      } else {
        $thiz.b = ((1 + $thiz.b) | 0);
        return "\\b";
      }
      break;
    }
    case 66: {
      if (((320 & $thiz.a4) !== 0)) {
        $thiz.gv("\\B with UNICODE_CASE", "2018");
      } else {
        $thiz.b = ((1 + $thiz.b) | 0);
        return "\\B";
      }
      break;
    }
    case 65: {
      $thiz.b = ((1 + $thiz.b) | 0);
      return "^";
      break;
    }
    case 71: {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "\\G in the middle of a pattern is not supported");
      break;
    }
    case 90: {
      $thiz.b = ((1 + $thiz.b) | 0);
      return (("(?=" + (((1 & $thiz.a4) !== 0) ? "\n" : "(?:\r\n?|[\n\u0085\u2028\u2029])")) + "?$)");
      break;
    }
    case 122: {
      $thiz.b = ((1 + $thiz.b) | 0);
      return "$";
      break;
    }
    case 82: {
      $thiz.b = ((1 + $thiz.b) | 0);
      return "(?:\r\n|[\n-\r\u0085\u2028\u2029])";
      break;
    }
    case 88: {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "\\X is not supported");
      break;
    }
    case 49:
    case 50:
    case 51:
    case 52:
    case 53:
    case 54:
    case 55:
    case 56:
    case 57: {
      var start = $thiz.b;
      var end = ((1 + start) | 0);
      while (true) {
        if (((end !== len) && ((((pattern.charCodeAt(end) - 48) | 0) >>> 0) <= 9))) {
          var s = pattern.substring(start, ((1 + end) | 0));
          var $x_1 = ((parseInt(s, 10) | 0) <= ((($thiz.cu.length | 0) - 1) | 0));
        } else {
          var $x_1 = false;
        }
        if ($x_1) {
          end = ((1 + end) | 0);
        } else {
          break;
        }
      }
      var groupString = pattern.substring(start, end);
      var groupNumber = (parseInt(groupString, 10) | 0);
      if ((groupNumber > ((($thiz.cu.length | 0) - 1) | 0))) {
        $p_ju_regex_PatternCompiler__parseError__T__E($thiz, (("numbered capturing group <" + groupNumber) + "> does not exist"));
      }
      var compiledGroupNumber = ($thiz.cu[groupNumber] | 0);
      $thiz.b = end;
      return (("(?:\\" + compiledGroupNumber) + ")");
      break;
    }
    case 107: {
      $thiz.b = ((1 + $thiz.b) | 0);
      if ((($thiz.b === len) || (pattern.charCodeAt($thiz.b) !== 60))) {
        $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "\\k is not followed by '<' for named capturing group");
      }
      $thiz.b = ((1 + $thiz.b) | 0);
      var groupName = $p_ju_regex_PatternCompiler__parseGroupName__T($thiz);
      var dict = $thiz.gL;
      if ((!(!(!$m_jl_Utils$Cache$().g4.call(dict, groupName))))) {
        $p_ju_regex_PatternCompiler__parseError__T__E($thiz, (("named capturing group <" + groupName) + "> does not exit"));
      }
      var groupNumber$2 = (dict[groupName] | 0);
      var compiledGroupNumber$2 = ($thiz.cu[groupNumber$2] | 0);
      $thiz.b = ((1 + $thiz.b) | 0);
      return (("(?:\\" + compiledGroupNumber$2) + ")");
      break;
    }
    case 81: {
      var start$2 = ((1 + $thiz.b) | 0);
      var end$2 = (pattern.indexOf("\\E", start$2) | 0);
      if ((end$2 < 0)) {
        $thiz.b = pattern.length;
        return $p_ju_regex_PatternCompiler__literal__T__T($thiz, pattern.substring(start$2));
      } else {
        $thiz.b = ((2 + end$2) | 0);
        return $p_ju_regex_PatternCompiler__literal__T__T($thiz, pattern.substring(start$2, end$2));
      }
      break;
    }
    default: {
      return $p_ju_regex_PatternCompiler__literal__I__T($thiz, $p_ju_regex_PatternCompiler__parseSingleCodePointEscape__I($thiz));
    }
  }
}
function $p_ju_regex_PatternCompiler__parseSingleCodePointEscape__I($thiz) {
  var pattern = $thiz.be;
  var x1 = $f_T__codePointAt__I__I(pattern, $thiz.b);
  switch (x1) {
    case 48: {
      return $p_ju_regex_PatternCompiler__parseOctalEscape__I($thiz);
      break;
    }
    case 120: {
      return $p_ju_regex_PatternCompiler__parseHexEscape__I($thiz);
      break;
    }
    case 117: {
      return $p_ju_regex_PatternCompiler__parseUnicodeHexEscape__I($thiz);
      break;
    }
    case 78: {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "\\N is not supported");
      break;
    }
    case 97: {
      $thiz.b = ((1 + $thiz.b) | 0);
      return 7;
      break;
    }
    case 116: {
      $thiz.b = ((1 + $thiz.b) | 0);
      return 9;
      break;
    }
    case 110: {
      $thiz.b = ((1 + $thiz.b) | 0);
      return 10;
      break;
    }
    case 102: {
      $thiz.b = ((1 + $thiz.b) | 0);
      return 12;
      break;
    }
    case 114: {
      $thiz.b = ((1 + $thiz.b) | 0);
      return 13;
      break;
    }
    case 101: {
      $thiz.b = ((1 + $thiz.b) | 0);
      return 27;
      break;
    }
    case 99: {
      $thiz.b = ((1 + $thiz.b) | 0);
      if (($thiz.b === pattern.length)) {
        $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Illegal control escape sequence");
      }
      var cp = $f_T__codePointAt__I__I(pattern, $thiz.b);
      $thiz.b = (($thiz.b + ((cp >= 65536) ? 2 : 1)) | 0);
      return (64 ^ cp);
      break;
    }
    default: {
      if ((((((x1 - 65) | 0) >>> 0) <= 25) || ((((x1 - 97) | 0) >>> 0) <= 25))) {
        $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Illegal/unsupported escape sequence");
      }
      $thiz.b = (($thiz.b + ((x1 >= 65536) ? 2 : 1)) | 0);
      return x1;
    }
  }
}
function $p_ju_regex_PatternCompiler__parseOctalEscape__I($thiz) {
  var pattern = $thiz.be;
  var len = pattern.length;
  var start = $thiz.b;
  var d1 = ((((1 + start) | 0) < len) ? ((pattern.charCodeAt(((1 + start) | 0)) - 48) | 0) : (-1));
  if (((d1 >>> 0) > 7)) {
    $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Illegal octal escape sequence");
  }
  var d2 = ((((2 + start) | 0) < len) ? ((pattern.charCodeAt(((2 + start) | 0)) - 48) | 0) : (-1));
  if (((d2 >>> 0) > 7)) {
    $thiz.b = ((2 + $thiz.b) | 0);
    return d1;
  } else if ((d1 > 3)) {
    $thiz.b = ((3 + $thiz.b) | 0);
    return (((d1 << 3) + d2) | 0);
  } else {
    var d3 = ((((3 + start) | 0) < len) ? ((pattern.charCodeAt(((3 + start) | 0)) - 48) | 0) : (-1));
    if (((d3 >>> 0) > 7)) {
      $thiz.b = ((3 + $thiz.b) | 0);
      return (((d1 << 3) + d2) | 0);
    } else {
      $thiz.b = ((4 + $thiz.b) | 0);
      return (((((d1 << 6) + (d2 << 3)) | 0) + d3) | 0);
    }
  }
}
function $p_ju_regex_PatternCompiler__parseHexEscape__I($thiz) {
  var pattern = $thiz.be;
  var len = pattern.length;
  var start = ((1 + $thiz.b) | 0);
  if (((start !== len) && (pattern.charCodeAt(start) === 123))) {
    var innerStart = ((1 + start) | 0);
    var innerEnd = (pattern.indexOf("}", innerStart) | 0);
    if ((innerEnd < 0)) {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Unclosed hexadecimal escape sequence");
    }
    var cp = $p_ju_regex_PatternCompiler__parseHexCodePoint__I__I__T__I($thiz, innerStart, innerEnd, "hexadecimal");
    $thiz.b = ((1 + innerEnd) | 0);
    return cp;
  } else {
    var cp$2 = $p_ju_regex_PatternCompiler__parseHexCodePoint__I__I__T__I($thiz, start, ((2 + start) | 0), "hexadecimal");
    $thiz.b = ((2 + start) | 0);
    return cp$2;
  }
}
function $p_ju_regex_PatternCompiler__parseUnicodeHexEscape__I($thiz) {
  var pattern = $thiz.be;
  var start = ((1 + $thiz.b) | 0);
  var end = ((4 + start) | 0);
  var codeUnit = $p_ju_regex_PatternCompiler__parseHexCodePoint__I__I__T__I($thiz, start, end, "Unicode");
  $thiz.b = end;
  var lowStart = ((2 + end) | 0);
  var lowEnd = ((4 + lowStart) | 0);
  if (((((-1024) & codeUnit) === 55296) && (pattern.substring(end, lowStart) === "\\u"))) {
    var low = $p_ju_regex_PatternCompiler__parseHexCodePoint__I__I__T__I($thiz, lowStart, lowEnd, "Unicode");
    if ((((-1024) & low) === 56320)) {
      $thiz.b = lowEnd;
      return ((((64 + (1023 & codeUnit)) | 0) << 10) | (1023 & low));
    } else {
      return codeUnit;
    }
  } else {
    return codeUnit;
  }
}
function $p_ju_regex_PatternCompiler__parseHexCodePoint__I__I__T__I($thiz, start, end, nameForError) {
  var pattern = $thiz.be;
  var len = pattern.length;
  if (((end === start) || (end > len))) {
    $p_ju_regex_PatternCompiler__parseError__T__E($thiz, (("Illegal " + nameForError) + " escape sequence"));
  }
  var i = start;
  while ((i < end)) {
    var value = i;
    var c = pattern.charCodeAt(value);
    if ((!((((((c - 48) | 0) >>> 0) <= 9) || ((((c - 65) | 0) >>> 0) <= 5)) || ((((c - 97) | 0) >>> 0) <= 5)))) {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, (("Illegal " + nameForError) + " escape sequence"));
    }
    i = ((1 + i) | 0);
  }
  if ((((end - start) | 0) > 6)) {
    var cp = 1114112;
  } else {
    var s = pattern.substring(start, end);
    var cp = (parseInt(s, 16) | 0);
  }
  if ((cp > 1114111)) {
    $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Hexadecimal codepoint is too big");
  }
  return cp;
}
function $p_ju_regex_PatternCompiler__parsePredefinedCharacterClass__C__ju_regex_PatternCompiler$CompiledCharClass($thiz, dispatchChar) {
  $thiz.b = ((1 + $thiz.b) | 0);
  switch (dispatchChar) {
    case 100:
    case 68: {
      var positive = $m_ju_regex_PatternCompiler$().mg;
      break;
    }
    case 104:
    case 72: {
      var positive = $m_ju_regex_PatternCompiler$().mj;
      break;
    }
    case 115:
    case 83: {
      var positive = $m_ju_regex_PatternCompiler$().mh;
      break;
    }
    case 118:
    case 86: {
      var positive = $m_ju_regex_PatternCompiler$().mk;
      break;
    }
    case 119:
    case 87: {
      var positive = $m_ju_regex_PatternCompiler$().mi;
      break;
    }
    case 112:
    case 80: {
      var positive = $p_ju_regex_PatternCompiler__parsePCharacterClass__ju_regex_PatternCompiler$CompiledCharClass($thiz);
      break;
    }
    default: {
      var positive;
      throw new $c_jl_AssertionError($bC(dispatchChar));
    }
  }
  return ((dispatchChar >= 97) ? positive : positive.ry());
}
function $p_ju_regex_PatternCompiler__parsePCharacterClass__ju_regex_PatternCompiler$CompiledCharClass($thiz) {
  var pattern = $thiz.be;
  var len = pattern.length;
  var start = $thiz.b;
  if ((start === len)) {
    var property = "?";
  } else if ((pattern.charCodeAt(start) === 123)) {
    var innerStart = ((1 + start) | 0);
    var innerEnd = (pattern.indexOf("}", innerStart) | 0);
    if ((innerEnd < 0)) {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Unclosed character family");
    }
    $thiz.b = innerEnd;
    var property = pattern.substring(innerStart, innerEnd);
  } else {
    var property = pattern.substring(start, ((1 + start) | 0));
  }
  var dict = $m_ju_regex_PatternCompiler$().ju;
  if ((!(!(!$m_jl_Utils$Cache$().g4.call(dict, property))))) {
    $thiz.gv("Unicode character family", "2018");
  }
  var property2 = ((((66 & $thiz.a4) === 2) && ((property === "Lower") || (property === "Upper"))) ? "Alpha" : property);
  var dict$1 = $m_ju_regex_PatternCompiler$().ju;
  var result = dict$1[property2];
  $thiz.b = ((1 + $thiz.b) | 0);
  return result;
}
function $p_ju_regex_PatternCompiler__compileCharacterClass__T($thiz) {
  var pattern = $thiz.be;
  var len = pattern.length;
  $thiz.b = ((1 + $thiz.b) | 0);
  var isNegated = (($thiz.b !== len) && (pattern.charCodeAt($thiz.b) === 94));
  if (isNegated) {
    $thiz.b = ((1 + $thiz.b) | 0);
  }
  var builder = new $c_ju_regex_PatternCompiler$CharacterClassBuilder(((66 & $thiz.a4) === 2), isNegated);
  while (($thiz.b !== len)) {
    var x1 = $f_T__codePointAt__I__I(pattern, $thiz.b);
    matchResult: {
      switch (x1) {
        case 93: {
          $thiz.b = ((1 + $thiz.b) | 0);
          return builder.qM();
          break;
        }
        case 38: {
          $thiz.b = ((1 + $thiz.b) | 0);
          if ((($thiz.b !== len) && (pattern.charCodeAt($thiz.b) === 38))) {
            $thiz.b = ((1 + $thiz.b) | 0);
            builder.sf();
            break matchResult;
          } else {
            $p_ju_regex_PatternCompiler__processRangeOrSingleCodePoint$1__I__I__T__ju_regex_PatternCompiler$CharacterClassBuilder__V($thiz, 38, len, pattern, builder);
            break matchResult;
          }
          break;
        }
        case 91: {
          $p_ju_regex_PatternCompiler$CharacterClassBuilder__addAlternative__T__V(builder, $p_ju_regex_PatternCompiler__compileCharacterClass__T($thiz));
          break matchResult;
          break;
        }
        case 92: {
          $thiz.b = ((1 + $thiz.b) | 0);
          if (($thiz.b === len)) {
            $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Illegal escape sequence");
          }
          var index$2 = $thiz.b;
          var c2 = pattern.charCodeAt(index$2);
          switch (c2) {
            case 100:
            case 68:
            case 104:
            case 72:
            case 115:
            case 83:
            case 118:
            case 86:
            case 119:
            case 87:
            case 112:
            case 80: {
              builder.pJ($p_ju_regex_PatternCompiler__parsePredefinedCharacterClass__C__ju_regex_PatternCompiler$CompiledCharClass($thiz, c2));
              break matchResult;
              break;
            }
            case 81: {
              $thiz.b = ((1 + $thiz.b) | 0);
              var fromIndex = $thiz.b;
              var end = (pattern.indexOf("\\E", fromIndex) | 0);
              if ((end < 0)) {
                $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Unclosed character class");
              }
              builder.pL(pattern, $thiz.b, end);
              $thiz.b = ((2 + end) | 0);
              break matchResult;
              break;
            }
            default: {
              $p_ju_regex_PatternCompiler__processRangeOrSingleCodePoint$1__I__I__T__ju_regex_PatternCompiler$CharacterClassBuilder__V($thiz, $p_ju_regex_PatternCompiler__parseSingleCodePointEscape__I($thiz), len, pattern, builder);
              break matchResult;
            }
          }
          break;
        }
        case 32:
        case 9:
        case 10:
        case 11:
        case 12:
        case 13: {
          if (((4 & $thiz.a4) !== 0)) {
            $thiz.b = ((1 + $thiz.b) | 0);
            break matchResult;
          }
          break;
        }
        case 35: {
          if (((4 & $thiz.a4) !== 0)) {
            $thiz.kY();
            break matchResult;
          }
          break;
        }
      }
      $thiz.b = (($thiz.b + ((x1 >= 65536) ? 2 : 1)) | 0);
      $p_ju_regex_PatternCompiler__processRangeOrSingleCodePoint$1__I__I__T__ju_regex_PatternCompiler$CharacterClassBuilder__V($thiz, x1, len, pattern, builder);
    }
  }
  $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Unclosed character class");
}
function $p_ju_regex_PatternCompiler__compileGroup__T($thiz) {
  var pattern = $thiz.be;
  var len = pattern.length;
  var start = $thiz.b;
  if (((((1 + start) | 0) === len) || (pattern.charCodeAt(((1 + start) | 0)) !== 63))) {
    $thiz.b = ((1 + start) | 0);
    $thiz.ct = ((1 + $thiz.ct) | 0);
    $thiz.cu.push($thiz.ct);
    return (("(" + $thiz.hM(true)) + ")");
  } else {
    if ((((2 + start) | 0) === len)) {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Unclosed group");
    }
    var index$1 = ((2 + start) | 0);
    var c1 = pattern.charCodeAt(index$1);
    if ((((c1 === 58) || (c1 === 61)) || (c1 === 33))) {
      $thiz.b = ((3 + start) | 0);
      return ((("" + pattern.substring(start, ((3 + start) | 0))) + $thiz.hM(true)) + ")");
    } else if ((c1 === 60)) {
      if ((((3 + start) | 0) === len)) {
        $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Unclosed group");
      }
      var index$2 = ((3 + start) | 0);
      var c2 = pattern.charCodeAt(index$2);
      if ((((((c2 - 65) | 0) >>> 0) <= 25) || ((((c2 - 97) | 0) >>> 0) <= 25))) {
        $thiz.b = ((3 + start) | 0);
        var name = $p_ju_regex_PatternCompiler__parseGroupName__T($thiz);
        var dict = $thiz.gL;
        if ((!(!$m_jl_Utils$Cache$().g4.call(dict, name)))) {
          $p_ju_regex_PatternCompiler__parseError__T__E($thiz, (("named capturing group <" + name) + "> is already defined"));
        }
        $thiz.ct = ((1 + $thiz.ct) | 0);
        $thiz.cu.push($thiz.ct);
        var dict$1 = $thiz.gL;
        var value = ((($thiz.cu.length | 0) - 1) | 0);
        dict$1[name] = value;
        $thiz.b = ((1 + $thiz.b) | 0);
        return (("(" + $thiz.hM(true)) + ")");
      } else {
        if (((c2 !== 33) && (c2 !== 61))) {
          $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Unknown look-behind group");
        }
        $thiz.gv("Look-behind group", "2018");
      }
    } else if ((c1 === 62)) {
      $thiz.b = ((3 + start) | 0);
      $thiz.ct = ((1 + $thiz.ct) | 0);
      var groupNumber = $thiz.ct;
      return (((("(?:(?=(" + $thiz.hM(true)) + "))\\") + groupNumber) + ")");
    } else {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Embedded flag expression in the middle of a pattern is not supported");
    }
  }
}
function $p_ju_regex_PatternCompiler__parseGroupName__T($thiz) {
  var pattern = $thiz.be;
  var len = pattern.length;
  var start = $thiz.b;
  while (true) {
    if (($thiz.b !== len)) {
      var index = $thiz.b;
      var c = pattern.charCodeAt(index);
      var $x_1 = ((((((c - 65) | 0) >>> 0) <= 25) || ((((c - 97) | 0) >>> 0) <= 25)) || ((((c - 48) | 0) >>> 0) <= 9));
    } else {
      var $x_1 = false;
    }
    if ($x_1) {
      $thiz.b = ((1 + $thiz.b) | 0);
    } else {
      break;
    }
  }
  if ((($thiz.b === len) || (pattern.charCodeAt($thiz.b) !== 62))) {
    $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "named capturing group is missing trailing '>'");
  }
  return pattern.substring(start, $thiz.b);
}
function $p_ju_regex_PatternCompiler__processRangeOrSingleCodePoint$1__I__I__T__ju_regex_PatternCompiler$CharacterClassBuilder__V($thiz, startCodePoint, len$2, pattern$3, builder$1) {
  if (((4 & $thiz.a4) !== 0)) {
    $p_ju_regex_PatternCompiler__skipComments__V($thiz);
  }
  if ((($thiz.b !== len$2) && (pattern$3.charCodeAt($thiz.b) === 45))) {
    $thiz.b = ((1 + $thiz.b) | 0);
    if (((4 & $thiz.a4) !== 0)) {
      $p_ju_regex_PatternCompiler__skipComments__V($thiz);
    }
    if (($thiz.b === len$2)) {
      $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Unclosed character class");
    }
    var cpEnd = $f_T__codePointAt__I__I(pattern$3, $thiz.b);
    if (((cpEnd === 91) || (cpEnd === 93))) {
      builder$1.iP(startCodePoint);
      builder$1.iP(45);
    } else {
      $thiz.b = (($thiz.b + ((cpEnd >= 65536) ? 2 : 1)) | 0);
      var endCodePoint = ((cpEnd === 92) ? $p_ju_regex_PatternCompiler__parseSingleCodePointEscape__I($thiz) : cpEnd);
      if ((endCodePoint < startCodePoint)) {
        $p_ju_regex_PatternCompiler__parseError__T__E($thiz, "Illegal character range");
      }
      builder$1.pK(startCodePoint, endCodePoint);
    }
  } else {
    builder$1.iP(startCodePoint);
  }
}
/** @constructor */
function $c_ju_regex_PatternCompiler(pattern, flags) {
  this.be = null;
  this.a4 = 0;
  this.i7 = false;
  this.b = 0;
  this.ct = 0;
  this.cu = null;
  this.gL = null;
  this.be = pattern;
  this.a4 = flags;
  this.i7 = false;
  this.b = 0;
  this.ct = 0;
  this.cu = [0];
  this.gL = ({});
}
$p = $c_ju_regex_PatternCompiler.prototype = new $h_O();
$p.constructor = $c_ju_regex_PatternCompiler;
/** @constructor */
function $h_ju_regex_PatternCompiler() {
}
$h_ju_regex_PatternCompiler.prototype = $p;
$p.qd = (function() {
  if (((256 & this.a4) !== 0)) {
    this.a4 = (64 | this.a4);
  }
  var isLiteral = ((16 & this.a4) !== 0);
  if ((!isLiteral)) {
    $p_ju_regex_PatternCompiler__processLeadingEmbeddedFlags__V(this);
  }
  if (((128 & this.a4) !== 0)) {
    $p_ju_regex_PatternCompiler__parseError__T__E(this, "CANON_EQ is not supported");
  }
  if (((8 & this.a4) !== 0)) {
    this.gv("MULTILINE", "2018");
  }
  if (((256 & this.a4) !== 0)) {
    this.gv("UNICODE_CHARACTER_CLASS", "2018");
  }
  if (isLiteral) {
    var jsPattern = $p_ju_regex_PatternCompiler__literal__T__T(this, this.be);
  } else {
    if ((this.be.substring(this.b, ((2 + this.b) | 0)) === "\\G")) {
      this.i7 = true;
      this.b = ((2 + this.b) | 0);
    }
    var jsPattern = this.hM(false);
  }
  var baseJSFlags = ($m_ju_regex_PatternCompiler$().jt ? "us" : "u");
  var jsFlags = (((66 & this.a4) === 66) ? (baseJSFlags + "i") : baseJSFlags);
  return new $c_ju_regex_Pattern(this.be, this.a4, jsPattern, jsFlags, this.i7, (((this.cu.length | 0) - 1) | 0), this.cu, this.gL);
});
$p.gv = (function(purpose, es) {
  $p_ju_regex_PatternCompiler__parseError__T__E(this, (((((((purpose + " is not supported because it requires RegExp features of ECMAScript ") + es) + ".\n") + ((("If you only target environments with ES" + es) + "+, you can enable ES") + es)) + " features with\n") + ("  scalaJSLinkerConfig ~= { _.withESFeatures(_.withESVersion(ESVersion.ES" + es)) + ")) }\nor an equivalent configuration depending on your build tool."));
});
$p.hM = (function(insideGroup) {
  var pattern = this.be;
  var len = pattern.length;
  var result = "";
  while ((this.b !== len)) {
    var dispatchCP = $f_T__codePointAt__I__I(pattern, this.b);
    matchResult: {
      switch (dispatchCP) {
        case 41: {
          if ((!insideGroup)) {
            $p_ju_regex_PatternCompiler__parseError__T__E(this, "Unmatched closing ')'");
          }
          this.b = ((1 + this.b) | 0);
          return result;
          break;
        }
        case 124: {
          if ((this.i7 && (!insideGroup))) {
            $p_ju_regex_PatternCompiler__parseError__T__E(this, "\\G is not supported when there is an alternative at the top level");
          }
          this.b = ((1 + this.b) | 0);
          result = (result + "|");
          break matchResult;
          break;
        }
        case 32:
        case 9:
        case 10:
        case 11:
        case 12:
        case 13: {
          if (((4 & this.a4) !== 0)) {
            this.b = ((1 + this.b) | 0);
            break matchResult;
          }
          break;
        }
        case 35: {
          if (((4 & this.a4) !== 0)) {
            this.kY();
            break matchResult;
          }
          break;
        }
        case 63:
        case 42:
        case 43:
        case 123: {
          $p_ju_regex_PatternCompiler__parseError__T__E(this, (("Dangling meta character '" + $m_ju_regex_PatternCompiler$().fG(dispatchCP)) + "'"));
          break;
        }
      }
      var compiledGroupCountBeforeThisToken = this.ct;
      switch (dispatchCP) {
        case 92: {
          var compiledToken = $p_ju_regex_PatternCompiler__compileEscape__T(this);
          break;
        }
        case 91: {
          var compiledToken = $p_ju_regex_PatternCompiler__compileCharacterClass__T(this);
          break;
        }
        case 40: {
          var compiledToken = $p_ju_regex_PatternCompiler__compileGroup__T(this);
          break;
        }
        case 94: {
          this.b = ((1 + this.b) | 0);
          var compiledToken = "^";
          break;
        }
        case 36: {
          this.b = ((1 + this.b) | 0);
          var compiledToken = "$";
          break;
        }
        case 46: {
          this.b = ((1 + this.b) | 0);
          var rejected = (((32 & this.a4) !== 0) ? "" : (((1 & this.a4) !== 0) ? "\n" : "\n\r\u0085\u2028\u2029"));
          var compiledToken = $m_ju_regex_PatternCompiler$().iZ(rejected);
          break;
        }
        default: {
          this.b = ((this.b + ((dispatchCP >= 65536) ? 2 : 1)) | 0);
          var compiledToken = $p_ju_regex_PatternCompiler__literal__I__T(this, dispatchCP);
        }
      }
      result = (("" + result) + $p_ju_regex_PatternCompiler__compileRepeater__I__T__T(this, compiledGroupCountBeforeThisToken, compiledToken));
    }
  }
  if (insideGroup) {
    $p_ju_regex_PatternCompiler__parseError__T__E(this, "Unclosed group");
  }
  return result;
});
$p.kY = (function() {
  var pattern = this.be;
  var len = pattern.length;
  while (true) {
    if ((this.b !== len)) {
      var index = this.b;
      var c = pattern.charCodeAt(index);
      var $x_1 = (!(((((c === 10) || (c === 13)) || (c === 133)) || (c === 8232)) || (c === 8233)));
    } else {
      var $x_1 = false;
    }
    if ($x_1) {
      this.b = ((1 + this.b) | 0);
    } else {
      break;
    }
  }
});
var $d_ju_regex_PatternCompiler = new $TypeData().i($c_ju_regex_PatternCompiler, "java.util.regex.PatternCompiler", ({
  ev: 1
}));
function $p_ju_regex_PatternCompiler$__featureTest__T__Z($thiz, flags) {
  try {
    new RegExp("", flags);
    return true;
  } catch (e) {
    return false;
  }
}
/** @constructor */
function $c_ju_regex_PatternCompiler$() {
  this.ml = null;
  this.mm = null;
  this.pd = false;
  this.pc = false;
  this.jt = false;
  this.mg = null;
  this.p9 = null;
  this.mj = null;
  this.mh = null;
  this.pa = null;
  this.mk = null;
  this.mi = null;
  this.pb = null;
  this.ju = null;
  this.pe = null;
  $n_ju_regex_PatternCompiler$ = this;
  this.ml = new RegExp("^\\(\\?([idmsuxU]*)(?:-([idmsuxU]*))?\\)");
  this.mm = new RegExp("(\\\\+)(\\d+)", "g");
  this.pd = true;
  this.pc = true;
  this.jt = $p_ju_regex_PatternCompiler$__featureTest__T__Z(this, "us");
  $p_ju_regex_PatternCompiler$__featureTest__T__Z(this, "d");
  this.mg = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "0-9");
  this.p9 = new $c_ju_regex_PatternCompiler$CompiledCharClass(0, "Nd");
  this.mj = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "\t \u00a0\u1680\u180e\u2000-\u200a\u202f\u205f\u3000");
  this.mh = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "\t-\r ");
  this.pa = new $c_ju_regex_PatternCompiler$CompiledCharClass(0, "White_Space");
  this.mk = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "\n-\r\u0085\u2028\u2029");
  this.mi = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "a-zA-Z_0-9");
  this.pb = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "\\p{Alphabetic}\\p{Mn}\\p{Me}\\p{Mc}\\p{Nd}\\p{Pc}\\p{Join_Control}");
  var r = ({});
  var value = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "a-z");
  r.Lower = value;
  var value$1 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "A-Z");
  r.Upper = value$1;
  var value$2 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "\u0000-\u007f");
  r.ASCII = value$2;
  var value$3 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "A-Za-z");
  r.Alpha = value$3;
  var value$4 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "0-9");
  r.Digit = value$4;
  var value$5 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "0-9A-Za-z");
  r.Alnum = value$5;
  var value$6 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "!-/:-@[-`{-~");
  r.Punct = value$6;
  var value$7 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "!-~");
  r.Graph = value$7;
  var value$8 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, " -~");
  r.Print = value$8;
  var value$9 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "\t ");
  r.Blank = value$9;
  var value$10 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "\u0000-\u001f\u007f");
  r.Cntrl = value$10;
  var value$11 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "0-9A-Fa-f");
  r.XDigit = value$11;
  var value$12 = new $c_ju_regex_PatternCompiler$CompiledCharClass(2, "\t-\r ");
  r.Space = value$12;
  this.ju = r;
  this.pe = new RegExp("(?:^|_)[a-z]", "g");
}
$p = $c_ju_regex_PatternCompiler$.prototype = new $h_O();
$p.constructor = $c_ju_regex_PatternCompiler$;
/** @constructor */
function $h_ju_regex_PatternCompiler$() {
}
$h_ju_regex_PatternCompiler$.prototype = $p;
$p.nX = (function(regex, flags) {
  return new $c_ju_regex_PatternCompiler(regex, flags).qd();
});
$p.ow = (function(c) {
  switch (c) {
    case 105: {
      return 2;
      break;
    }
    case 100: {
      return 1;
      break;
    }
    case 109: {
      return 8;
      break;
    }
    case 115: {
      return 32;
      break;
    }
    case 117: {
      return 64;
      break;
    }
    case 120: {
      return 4;
      break;
    }
    case 85: {
      return 256;
      break;
    }
    default: {
      throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), "bad in-pattern flag");
    }
  }
});
$p.iZ = (function(characters) {
  return ((characters !== "") ? (("[^" + characters) + "]") : ($m_ju_regex_PatternCompiler$().jt ? "." : "[\\d\\D]"));
});
$p.fG = (function(codePoint) {
  return String.fromCodePoint(codePoint);
});
var $d_ju_regex_PatternCompiler$ = new $TypeData().i($c_ju_regex_PatternCompiler$, "java.util.regex.PatternCompiler$", ({
  ew: 1
}));
var $n_ju_regex_PatternCompiler$;
function $m_ju_regex_PatternCompiler$() {
  if ((!$n_ju_regex_PatternCompiler$)) {
    $n_ju_regex_PatternCompiler$ = new $c_ju_regex_PatternCompiler$();
  }
  return $n_ju_regex_PatternCompiler$;
}
function $p_ju_regex_PatternCompiler$CharacterClassBuilder__addAlternative__T__V($thiz, alt) {
  if (($thiz.cM === "")) {
    $thiz.cM = alt;
  } else {
    $thiz.cM = (($thiz.cM + "|") + alt);
  }
}
function $p_ju_regex_PatternCompiler$CharacterClassBuilder__conjunctResult__T($thiz) {
  if ($thiz.jw) {
    var negThisSegment = $m_ju_regex_PatternCompiler$().iZ($thiz.ar);
    return (($thiz.cM === "") ? negThisSegment : (((("(?:(?!" + $thiz.cM) + ")") + negThisSegment) + ")"));
  } else {
    return (($thiz.ar === "") ? (($thiz.cM === "") ? "[^\\d\\D]" : (("(?:" + $thiz.cM) + ")")) : (($thiz.cM === "") ? (("[" + $thiz.ar) + "]") : (((("(?:" + $thiz.cM) + "|[") + $thiz.ar) + "])")));
  }
}
function $p_ju_regex_PatternCompiler$CharacterClassBuilder__literalCodePoint__I__T($thiz, codePoint) {
  var s = $m_ju_regex_PatternCompiler$().fG(codePoint);
  return (((((((codePoint - 92) | 0) >>> 0) <= 1) || (codePoint === 45)) || (codePoint === 94)) ? ("\\" + s) : s);
}
/** @constructor */
function $c_ju_regex_PatternCompiler$CharacterClassBuilder(asciiCaseInsensitive, isNegated) {
  this.jv = false;
  this.jw = false;
  this.gM = null;
  this.cM = null;
  this.ar = null;
  this.jv = asciiCaseInsensitive;
  this.jw = isNegated;
  this.gM = "";
  this.cM = "";
  this.ar = "";
}
$p = $c_ju_regex_PatternCompiler$CharacterClassBuilder.prototype = new $h_O();
$p.constructor = $c_ju_regex_PatternCompiler$CharacterClassBuilder;
/** @constructor */
function $h_ju_regex_PatternCompiler$CharacterClassBuilder() {
}
$h_ju_regex_PatternCompiler$CharacterClassBuilder.prototype = $p;
$p.qM = (function() {
  var conjunct = $p_ju_regex_PatternCompiler$CharacterClassBuilder__conjunctResult__T(this);
  return ((this.gM === "") ? conjunct : ((("(?:" + this.gM) + conjunct) + ")"));
});
$p.sf = (function() {
  var conjunct = $p_ju_regex_PatternCompiler$CharacterClassBuilder__conjunctResult__T(this);
  this.gM = (this.gM + (this.jw ? (conjunct + "|") : (("(?=" + conjunct) + ")")));
  this.cM = "";
  this.ar = "";
});
$p.pJ = (function(cls) {
  var x1 = cls.i8;
  switch (x1) {
    case 0: {
      this.ar = ((this.ar + ("\\p{" + cls.dz)) + "}");
      break;
    }
    case 1: {
      this.ar = ((this.ar + ("\\P{" + cls.dz)) + "}");
      break;
    }
    case 2: {
      this.ar = (("" + this.ar) + cls.dz);
      break;
    }
    case 3: {
      $p_ju_regex_PatternCompiler$CharacterClassBuilder__addAlternative__T__V(this, $m_ju_regex_PatternCompiler$().iZ(cls.dz));
      break;
    }
    default: {
      throw new $c_jl_AssertionError(x1);
    }
  }
});
$p.pL = (function(str, start, end) {
  var i = start;
  while ((i !== end)) {
    var codePoint = $f_T__codePointAt__I__I(str, i);
    this.iP(codePoint);
    i = ((i + ((codePoint >= 65536) ? 2 : 1)) | 0);
  }
});
$p.iP = (function(codePoint) {
  var s = $p_ju_regex_PatternCompiler$CharacterClassBuilder__literalCodePoint__I__T(this, codePoint);
  if ((((-1024) & codePoint) === 56320)) {
    this.ar = (("" + s) + this.ar);
  } else {
    this.ar = (("" + this.ar) + s);
  }
  if (this.jv) {
    if (((((codePoint - 65) | 0) >>> 0) <= 25)) {
      this.ar = (("" + this.ar) + $m_ju_regex_PatternCompiler$().fG(((32 + codePoint) | 0)));
    } else if (((((codePoint - 97) | 0) >>> 0) <= 25)) {
      this.ar = (("" + this.ar) + $m_ju_regex_PatternCompiler$().fG(((codePoint - 32) | 0)));
    }
  }
});
$p.pK = (function(startCodePoint, endCodePoint) {
  var s = (($p_ju_regex_PatternCompiler$CharacterClassBuilder__literalCodePoint__I__T(this, startCodePoint) + "-") + $p_ju_regex_PatternCompiler$CharacterClassBuilder__literalCodePoint__I__T(this, endCodePoint));
  if ((((-1024) & startCodePoint) === 56320)) {
    this.ar = (s + this.ar);
  } else {
    this.ar = (this.ar + s);
  }
  if (this.jv) {
    var start = ((startCodePoint > 65) ? startCodePoint : 65);
    var end = ((endCodePoint < 90) ? endCodePoint : 90);
    if ((start <= end)) {
      var $x_1 = this.ar;
      var start$1 = ((32 + start) | 0);
      var end$1 = ((32 + end) | 0);
      this.ar = ($x_1 + (($p_ju_regex_PatternCompiler$CharacterClassBuilder__literalCodePoint__I__T(this, start$1) + "-") + $p_ju_regex_PatternCompiler$CharacterClassBuilder__literalCodePoint__I__T(this, end$1)));
    }
    var start$2 = ((startCodePoint > 97) ? startCodePoint : 97);
    var end$2 = ((endCodePoint < 122) ? endCodePoint : 122);
    if ((start$2 <= end$2)) {
      var $x_2 = this.ar;
      var start$3 = ((start$2 - 32) | 0);
      var end$3 = ((end$2 - 32) | 0);
      this.ar = ($x_2 + (($p_ju_regex_PatternCompiler$CharacterClassBuilder__literalCodePoint__I__T(this, start$3) + "-") + $p_ju_regex_PatternCompiler$CharacterClassBuilder__literalCodePoint__I__T(this, end$3)));
    }
  }
});
var $d_ju_regex_PatternCompiler$CharacterClassBuilder = new $TypeData().i($c_ju_regex_PatternCompiler$CharacterClassBuilder, "java.util.regex.PatternCompiler$CharacterClassBuilder", ({
  ex: 1
}));
function $p_ju_regex_PatternCompiler$CompiledCharClass__negated$lzycompute__ju_regex_PatternCompiler$CompiledCharClass($thiz) {
  if ((!$thiz.jx)) {
    $thiz.jy = new $c_ju_regex_PatternCompiler$CompiledCharClass((1 ^ $thiz.i8), $thiz.dz);
    $thiz.jx = true;
  }
  return $thiz.jy;
}
/** @constructor */
function $c_ju_regex_PatternCompiler$CompiledCharClass(kind, data) {
  this.jy = null;
  this.i8 = 0;
  this.dz = null;
  this.jx = false;
  this.i8 = kind;
  this.dz = data;
}
$p = $c_ju_regex_PatternCompiler$CompiledCharClass.prototype = new $h_O();
$p.constructor = $c_ju_regex_PatternCompiler$CompiledCharClass;
/** @constructor */
function $h_ju_regex_PatternCompiler$CompiledCharClass() {
}
$h_ju_regex_PatternCompiler$CompiledCharClass.prototype = $p;
$p.ry = (function() {
  return ((!this.jx) ? $p_ju_regex_PatternCompiler$CompiledCharClass__negated$lzycompute__ju_regex_PatternCompiler$CompiledCharClass(this) : this.jy);
});
var $d_ju_regex_PatternCompiler$CompiledCharClass = new $TypeData().i($c_ju_regex_PatternCompiler$CompiledCharClass, "java.util.regex.PatternCompiler$CompiledCharClass", ({
  ey: 1
}));
function $s_RTLong__remainderUnsigned__I__I__I__I__J(alo, ahi, blo, bhi) {
  return $m_RTLong$().rN(alo, ahi, blo, bhi);
}
function $s_RTLong__remainder__I__I__I__I__J(alo, ahi, blo, bhi) {
  return $m_RTLong$().rM(alo, ahi, blo, bhi);
}
function $s_RTLong__divideUnsigned__I__I__I__I__J(alo, ahi, blo, bhi) {
  return $m_RTLong$().qt(alo, ahi, blo, bhi);
}
function $s_RTLong__divide__I__I__I__I__J(alo, ahi, blo, bhi) {
  return $m_RTLong$().qs(alo, ahi, blo, bhi);
}
function $s_RTLong__fromDoubleBits__D__O__J(value, fpBitsDataView) {
  fpBitsDataView.setFloat64(0, value, true);
  var lo = (fpBitsDataView.getInt32(0, true) | 0);
  var hi = (fpBitsDataView.getInt32(4, true) | 0);
  return $bL(lo, hi);
}
function $s_RTLong__fromDouble__D__J(value) {
  return $m_RTLong$().kL(value);
}
function $s_RTLong__fromUnsignedInt__I__J(value) {
  return $bL(value, 0);
}
function $s_RTLong__fromInt__I__J(value) {
  var hi = (value >> 31);
  return $bL(value, hi);
}
function $s_RTLong__clz__I__I__I(lo, hi) {
  return ((hi !== 0) ? Math.clz32(hi) : ((32 + Math.clz32(lo)) | 0));
}
function $s_RTLong__toFloat__I__I__F(lo, hi) {
  return Math.fround(((4.294967296E9 * hi) + ((((((-2097152) & (hi ^ (hi >> 10))) === 0) || ((65535 & lo) === 0)) ? lo : (32768 | ((-32768) & lo))) >>> 0.0)));
}
function $s_RTLong__toDouble__I__I__D(lo, hi) {
  return ((4.294967296E9 * hi) + (lo >>> 0.0));
}
function $s_RTLong__toInt__I__I__I(lo, hi) {
  return lo;
}
function $s_RTLong__toString__I__I__T(lo, hi) {
  return $m_RTLong$().lb(lo, hi);
}
function $s_RTLong__bitsToDouble__I__I__O__D(lo, hi, fpBitsDataView) {
  fpBitsDataView.setInt32(0, lo, true);
  fpBitsDataView.setInt32(4, hi, true);
  return (+fpBitsDataView.getFloat64(0, true));
}
function $s_RTLong__mul__I__I__I__I__J(alo, ahi, blo, bhi) {
  var a0 = (65535 & alo);
  var a1 = ((alo >>> 16) | 0);
  var b0 = (65535 & blo);
  var b1 = ((blo >>> 16) | 0);
  var a0b0 = Math.imul(a0, b0);
  var a1b0 = Math.imul(a1, b0);
  var a0b1 = Math.imul(a0, b1);
  var lo = ((a0b0 + (((a1b0 + a0b1) | 0) << 16)) | 0);
  var c1part = ((((a0b0 >>> 16) | 0) + a0b1) | 0);
  var hi = ((((((((Math.imul(alo, bhi) + Math.imul(ahi, blo)) | 0) + Math.imul(a1, b1)) | 0) + ((c1part >>> 16) | 0)) | 0) + (((((65535 & c1part) + a1b0) | 0) >>> 16) | 0)) | 0);
  return $bL(lo, hi);
}
function $s_RTLong__sub__I__I__I__I__J(alo, ahi, blo, bhi) {
  var lo = ((alo - blo) | 0);
  var hi = ((((ahi - bhi) | 0) - (((lo >>> 0) > (alo >>> 0)) | 0)) | 0);
  return $bL(lo, hi);
}
function $s_RTLong__add__I__I__I__I__J(alo, ahi, blo, bhi) {
  var lo = ((alo + blo) | 0);
  var hi = ((((ahi + bhi) | 0) + (((lo >>> 0) < (alo >>> 0)) | 0)) | 0);
  return $bL(lo, hi);
}
function $s_RTLong__sar__I__I__I__J(lo, hi, n) {
  var lo$1 = (((32 & n) === 0) ? (((lo >>> n) | 0) | ((hi << 1) << (~n))) : (hi >> n));
  var hi$1 = (((32 & n) === 0) ? (hi >> n) : (hi >> 31));
  return $bL(lo$1, hi$1);
}
function $s_RTLong__shr__I__I__I__J(lo, hi, n) {
  var lo$1 = (((32 & n) === 0) ? (((lo >>> n) | 0) | ((hi << 1) << (~n))) : ((hi >>> n) | 0));
  var hi$1 = (((32 & n) === 0) ? ((hi >>> n) | 0) : 0);
  return $bL(lo$1, hi$1);
}
function $s_RTLong__shl__I__I__I__J(lo, hi, n) {
  var lo$1 = (((32 & n) === 0) ? (lo << n) : 0);
  var hi$1 = (((32 & n) === 0) ? (((((lo >>> 1) | 0) >>> (~n)) | 0) | (hi << n)) : (lo << n));
  return $bL(lo$1, hi$1);
}
function $s_RTLong__xor__I__I__I__I__J(alo, ahi, blo, bhi) {
  var lo = (alo ^ blo);
  var hi = (ahi ^ bhi);
  return $bL(lo, hi);
}
function $s_RTLong__and__I__I__I__I__J(alo, ahi, blo, bhi) {
  var lo = (alo & blo);
  var hi = (ahi & bhi);
  return $bL(lo, hi);
}
function $s_RTLong__or__I__I__I__I__J(alo, ahi, blo, bhi) {
  var lo = (alo | blo);
  var hi = (ahi | bhi);
  return $bL(lo, hi);
}
function $s_RTLong__geu__I__I__I__I__Z(alo, ahi, blo, bhi) {
  return ((ahi === bhi) ? ((alo >>> 0) >= (blo >>> 0)) : ((ahi >>> 0) > (bhi >>> 0)));
}
function $s_RTLong__gtu__I__I__I__I__Z(alo, ahi, blo, bhi) {
  return ((ahi === bhi) ? ((alo >>> 0) > (blo >>> 0)) : ((ahi >>> 0) > (bhi >>> 0)));
}
function $s_RTLong__leu__I__I__I__I__Z(alo, ahi, blo, bhi) {
  return ((ahi === bhi) ? ((alo >>> 0) <= (blo >>> 0)) : ((ahi >>> 0) < (bhi >>> 0)));
}
function $s_RTLong__ltu__I__I__I__I__Z(alo, ahi, blo, bhi) {
  return ((ahi === bhi) ? ((alo >>> 0) < (blo >>> 0)) : ((ahi >>> 0) < (bhi >>> 0)));
}
function $s_RTLong__ge__I__I__I__I__Z(alo, ahi, blo, bhi) {
  return ((ahi === bhi) ? ((alo >>> 0) >= (blo >>> 0)) : (ahi > bhi));
}
function $s_RTLong__gt__I__I__I__I__Z(alo, ahi, blo, bhi) {
  return ((ahi === bhi) ? ((alo >>> 0) > (blo >>> 0)) : (ahi > bhi));
}
function $s_RTLong__le__I__I__I__I__Z(alo, ahi, blo, bhi) {
  return ((ahi === bhi) ? ((alo >>> 0) <= (blo >>> 0)) : (ahi < bhi));
}
function $s_RTLong__lt__I__I__I__I__Z(alo, ahi, blo, bhi) {
  return ((ahi === bhi) ? ((alo >>> 0) < (blo >>> 0)) : (ahi < bhi));
}
function $s_RTLong__notEquals__I__I__I__I__Z(alo, ahi, blo, bhi) {
  return (((alo ^ blo) | (ahi ^ bhi)) !== 0);
}
function $s_RTLong__equals__I__I__I__I__Z(alo, ahi, blo, bhi) {
  return (((alo ^ blo) | (ahi ^ bhi)) === 0);
}
/** @constructor */
function $c_RTLong$() {
}
$p = $c_RTLong$.prototype = new $h_O();
$p.constructor = $c_RTLong$;
/** @constructor */
function $h_RTLong$() {
}
$h_RTLong$.prototype = $p;
$p.lb = (function(lo, hi) {
  if ((hi === (lo >> 31))) {
    return ("" + lo);
  } else if ((((-2097152) & (hi ^ (hi >> 10))) === 0)) {
    return ("" + ((4.294967296E9 * hi) + (lo >>> 0.0)));
  } else {
    var sign = (hi >> 31);
    var xlo = (lo ^ sign);
    var rlo = ((xlo - sign) | 0);
    var rhi = (((hi ^ sign) + (((rlo >>> 0) < (xlo >>> 0)) | 0)) | 0);
    var aHat = ((4.294967296E9 * (rhi >>> 0.0)) + (rlo >>> 0.0));
    var qHat = (+Math.floor((1.0000000000000265E-9 * aHat)));
    var rHat = ((rlo - Math.imul(1000000000, (qHat | 0.0))) | 0);
    if ((rHat < 0)) {
      qHat = (qHat - 1.0);
      rHat = ((1000000000 + rHat) | 0);
    }
    var this$7 = rHat;
    var remStr = ("" + this$7);
    var $x_1 = qHat;
    var start = remStr.length;
    var s = ((("" + $x_1) + "000000000".substring(start)) + remStr);
    return ((hi < 0) ? ("-" + s) : s);
  }
});
$p.kL = (function(value) {
  if ((value < (-9.223372036854776E18))) {
    return $bL(0, (-2147483648));
  } else if ((value >= 9.223372036854776E18)) {
    return $bL((-1), 2147483647);
  } else {
    var rawLo = (value | 0.0);
    var rawHi = ((2.3283064365386963E-10 * value) | 0.0);
    var hi = (((value < 0.0) && (rawLo !== 0)) ? ((rawHi - 1) | 0) : rawHi);
    return $bL(rawLo, hi);
  }
});
$p.qs = (function(alo, ahi, blo, bhi) {
  var sign = (ahi >> 31);
  var xlo = (alo ^ sign);
  var rlo = ((xlo - sign) | 0);
  var rhi = (((ahi ^ sign) + (((rlo >>> 0) < (xlo >>> 0)) | 0)) | 0);
  var sign$1 = (bhi >> 31);
  var xlo$1 = (blo ^ sign$1);
  var rlo$1 = ((xlo$1 - sign$1) | 0);
  var rhi$1 = (((bhi ^ sign$1) + (((rlo$1 >>> 0) < (xlo$1 >>> 0)) | 0)) | 0);
  if (((rhi$1 | ((-2097152) & rlo$1)) === 0)) {
    var quotHi = (((rhi >>> 0) / ($checkIntDivisor(rlo$1) >>> 0)) | 0);
    var k = ((rhi - Math.imul(rlo$1, quotHi)) | 0);
    var quotLo = ((((4.294967296E9 * k) + (rlo >>> 0.0)) / rlo$1) | 0.0);
    var absR_$_lo = quotLo;
    var absR_$_hi = quotHi;
  } else {
    var aHat = ((4.294967296E9 * (rhi >>> 0.0)) + (rlo >>> 0.0));
    var bHat = ((4.294967296E9 * (rhi$1 >>> 0.0)) + (rlo$1 >>> 0.0));
    var x$1 = ((aHat / bHat) + 0.00390625);
    var lo = (x$1 | 0.0);
    var hi = ((2.3283064365386963E-10 * x$1) | 0.0);
    var a0 = (65535 & rlo$1);
    var a1 = ((rlo$1 >>> 16) | 0);
    var b0 = (65535 & lo);
    var b1 = ((lo >>> 16) | 0);
    var a0b0 = Math.imul(a0, b0);
    var a1b0 = Math.imul(a1, b0);
    var a0b1 = Math.imul(a0, b1);
    var lo$1 = ((a0b0 + (((a1b0 + a0b1) | 0) << 16)) | 0);
    var c1part = ((((a0b0 >>> 16) | 0) + a0b1) | 0);
    if ((((((rhi - ((((((((Math.imul(rlo$1, hi) + Math.imul(rhi$1, lo)) | 0) + Math.imul(a1, b1)) | 0) + ((c1part >>> 16) | 0)) | 0) + (((((65535 & c1part) + a1b0) | 0) >>> 16) | 0)) | 0)) | 0) - (((((rlo - lo$1) | 0) >>> 0) > (rlo >>> 0)) | 0)) | 0) < 0)) {
      var lo$3 = ((lo - 1) | 0);
      var hi$3 = ((((hi - 1) | 0) + ((lo$3 !== (-1)) | 0)) | 0);
      var absR_$_lo = lo$3;
      var absR_$_hi = hi$3;
    } else {
      var absR_$_lo = lo;
      var absR_$_hi = hi;
    }
  }
  if (((ahi ^ bhi) >= 0)) {
    return $bL(absR_$_lo, absR_$_hi);
  } else {
    var lo$4 = ((-absR_$_lo) | 0);
    var hi$4 = ((((-absR_$_hi) | 0) - ((lo$4 !== 0) | 0)) | 0);
    return $bL(lo$4, hi$4);
  }
});
$p.qt = (function(alo, ahi, blo, bhi) {
  if (((bhi | ((-2097152) & blo)) === 0)) {
    var quotHi = (((ahi >>> 0) / ($checkIntDivisor(blo) >>> 0)) | 0);
    var k = ((ahi - Math.imul(blo, quotHi)) | 0);
    var quotLo = ((((4.294967296E9 * k) + (alo >>> 0.0)) / blo) | 0.0);
    return $bL(quotLo, quotHi);
  } else if ((bhi >= 0)) {
    var aHat = ((4.294967296E9 * (ahi >>> 0.0)) + (alo >>> 0.0));
    var bHat = ((4.294967296E9 * (bhi >>> 0.0)) + (blo >>> 0.0));
    var x$1 = ((aHat / bHat) + 0.00390625);
    var lo = (x$1 | 0.0);
    var hi = ((2.3283064365386963E-10 * x$1) | 0.0);
    var a0 = (65535 & blo);
    var a1 = ((blo >>> 16) | 0);
    var b0 = (65535 & lo);
    var b1 = ((lo >>> 16) | 0);
    var a0b0 = Math.imul(a0, b0);
    var a1b0 = Math.imul(a1, b0);
    var a0b1 = Math.imul(a0, b1);
    var lo$1 = ((a0b0 + (((a1b0 + a0b1) | 0) << 16)) | 0);
    var c1part = ((((a0b0 >>> 16) | 0) + a0b1) | 0);
    if ((((((ahi - ((((((((Math.imul(blo, hi) + Math.imul(bhi, lo)) | 0) + Math.imul(a1, b1)) | 0) + ((c1part >>> 16) | 0)) | 0) + (((((65535 & c1part) + a1b0) | 0) >>> 16) | 0)) | 0)) | 0) - (((((alo - lo$1) | 0) >>> 0) > (alo >>> 0)) | 0)) | 0) < 0)) {
      var lo$3 = ((lo - 1) | 0);
      var hi$3 = ((((hi - 1) | 0) + ((lo$3 !== (-1)) | 0)) | 0);
      return $bL(lo$3, hi$3);
    } else {
      return $bL(lo, hi);
    }
  } else if (((ahi === bhi) ? ((alo >>> 0) < (blo >>> 0)) : ((ahi >>> 0) < (bhi >>> 0)))) {
    return $bL(0, 0);
  } else {
    return $bL(1, 0);
  }
});
$p.rM = (function(alo, ahi, blo, bhi) {
  var sign = (ahi >> 31);
  var xlo = (alo ^ sign);
  var rlo = ((xlo - sign) | 0);
  var rhi = (((ahi ^ sign) + (((rlo >>> 0) < (xlo >>> 0)) | 0)) | 0);
  var sign$1 = (bhi >> 31);
  var xlo$1 = (blo ^ sign$1);
  var rlo$1 = ((xlo$1 - sign$1) | 0);
  var rhi$1 = (((bhi ^ sign$1) + (((rlo$1 >>> 0) < (xlo$1 >>> 0)) | 0)) | 0);
  if (((rhi$1 | ((-2097152) & rlo$1)) === 0)) {
    var k$2 = (((rhi >>> 0) % ($checkIntDivisor(rlo$1) >>> 0)) | 0);
    var quotLo$2 = ((((4.294967296E9 * k$2) + (rlo >>> 0.0)) / rlo$1) | 0.0);
    var remLo = ((rlo - Math.imul(rlo$1, quotLo$2)) | 0);
    var absR_$_lo = remLo;
    var absR_$_hi = 0;
  } else {
    var aHat = ((4.294967296E9 * (rhi >>> 0.0)) + (rlo >>> 0.0));
    var bHat = ((4.294967296E9 * (rhi$1 >>> 0.0)) + (rlo$1 >>> 0.0));
    var x$1 = ((aHat / bHat) + 0.00390625);
    var lo = (x$1 | 0.0);
    var hi = ((2.3283064365386963E-10 * x$1) | 0.0);
    var a0 = (65535 & rlo$1);
    var a1 = ((rlo$1 >>> 16) | 0);
    var b0 = (65535 & lo);
    var b1 = ((lo >>> 16) | 0);
    var a0b0 = Math.imul(a0, b0);
    var a1b0 = Math.imul(a1, b0);
    var a0b1 = Math.imul(a0, b1);
    var lo$1 = ((a0b0 + (((a1b0 + a0b1) | 0) << 16)) | 0);
    var c1part = ((((a0b0 >>> 16) | 0) + a0b1) | 0);
    var hi$1 = ((((((((Math.imul(rlo$1, hi) + Math.imul(rhi$1, lo)) | 0) + Math.imul(a1, b1)) | 0) + ((c1part >>> 16) | 0)) | 0) + (((((65535 & c1part) + a1b0) | 0) >>> 16) | 0)) | 0);
    var lo$2 = ((rlo - lo$1) | 0);
    var hi$2 = ((((rhi - hi$1) | 0) - (((lo$2 >>> 0) > (rlo >>> 0)) | 0)) | 0);
    if ((hi$2 < 0)) {
      var lo$3 = ((lo$2 + rlo$1) | 0);
      var hi$3 = ((((hi$2 + rhi$1) | 0) + (((lo$3 >>> 0) < (lo$2 >>> 0)) | 0)) | 0);
      var absR_$_lo = lo$3;
      var absR_$_hi = hi$3;
    } else {
      var absR_$_lo = lo$2;
      var absR_$_hi = hi$2;
    }
  }
  if ((ahi < 0)) {
    var lo$4 = ((-absR_$_lo) | 0);
    var hi$4 = ((((-absR_$_hi) | 0) - ((lo$4 !== 0) | 0)) | 0);
    return $bL(lo$4, hi$4);
  } else {
    return $bL(absR_$_lo, absR_$_hi);
  }
});
$p.rN = (function(alo, ahi, blo, bhi) {
  if (((bhi | ((-2097152) & blo)) === 0)) {
    var k$2 = (((ahi >>> 0) % ($checkIntDivisor(blo) >>> 0)) | 0);
    var quotLo$2 = ((((4.294967296E9 * k$2) + (alo >>> 0.0)) / blo) | 0.0);
    var remLo = ((alo - Math.imul(blo, quotLo$2)) | 0);
    return $bL(remLo, 0);
  } else if ((bhi >= 0)) {
    var aHat = ((4.294967296E9 * (ahi >>> 0.0)) + (alo >>> 0.0));
    var bHat = ((4.294967296E9 * (bhi >>> 0.0)) + (blo >>> 0.0));
    var x$1 = ((aHat / bHat) + 0.00390625);
    var lo = (x$1 | 0.0);
    var hi = ((2.3283064365386963E-10 * x$1) | 0.0);
    var a0 = (65535 & blo);
    var a1 = ((blo >>> 16) | 0);
    var b0 = (65535 & lo);
    var b1 = ((lo >>> 16) | 0);
    var a0b0 = Math.imul(a0, b0);
    var a1b0 = Math.imul(a1, b0);
    var a0b1 = Math.imul(a0, b1);
    var lo$1 = ((a0b0 + (((a1b0 + a0b1) | 0) << 16)) | 0);
    var c1part = ((((a0b0 >>> 16) | 0) + a0b1) | 0);
    var hi$1 = ((((((((Math.imul(blo, hi) + Math.imul(bhi, lo)) | 0) + Math.imul(a1, b1)) | 0) + ((c1part >>> 16) | 0)) | 0) + (((((65535 & c1part) + a1b0) | 0) >>> 16) | 0)) | 0);
    var lo$2 = ((alo - lo$1) | 0);
    var hi$2 = ((((ahi - hi$1) | 0) - (((lo$2 >>> 0) > (alo >>> 0)) | 0)) | 0);
    if ((hi$2 < 0)) {
      var lo$3 = ((lo$2 + blo) | 0);
      var hi$3 = ((((hi$2 + bhi) | 0) + (((lo$3 >>> 0) < (lo$2 >>> 0)) | 0)) | 0);
      return $bL(lo$3, hi$3);
    } else {
      return $bL(lo$2, hi$2);
    }
  } else if (((ahi === bhi) ? ((alo >>> 0) < (blo >>> 0)) : ((ahi >>> 0) < (bhi >>> 0)))) {
    return $bL(alo, ahi);
  } else {
    var lo$4 = ((alo - blo) | 0);
    var hi$4 = ((((ahi - bhi) | 0) - (((lo$4 >>> 0) > (alo >>> 0)) | 0)) | 0);
    return $bL(lo$4, hi$4);
  }
});
var $d_RTLong$ = new $TypeData().i($c_RTLong$, "org.scalajs.linker.runtime.RuntimeLong$", ({
  eC: 1
}));
var $n_RTLong$;
function $m_RTLong$() {
  if ((!$n_RTLong$)) {
    $n_RTLong$ = new $c_RTLong$();
  }
  return $n_RTLong$;
}
function $p_s_Array$__slowcopy__O__I__O__I__I__V($thiz, src, srcPos, dest, destPos, length) {
  var i = srcPos;
  var j = destPos;
  var srcUntil = ((srcPos + length) | 0);
  while ((i < srcUntil)) {
    $m_sr_ScalaRunTime$().d3(dest, j, $m_sr_ScalaRunTime$().aM(src, i));
    i = ((1 + i) | 0);
    j = ((1 + j) | 0);
  }
}
function $p_s_Array$__newUnitArray__I__Ajl_Void($thiz, len) {
  var result = new ($d_jl_Void.r().C)(len);
  $m_ju_Arrays$().qI(result, (void 0));
  return result;
}
/** @constructor */
function $c_s_Array$() {
}
$p = $c_s_Array$.prototype = new $h_O();
$p.constructor = $c_s_Array$;
/** @constructor */
function $h_s_Array$() {
}
$h_s_Array$.prototype = $p;
$p.oh = (function(it, evidence$1) {
  var n = it.q();
  if ((n > (-1))) {
    var elements = evidence$1.bU(n);
    var iterator = it.c();
    var i = 0;
    while ((i < n)) {
      $m_sr_ScalaRunTime$().d3(elements, i, iterator.f());
      i = ((1 + i) | 0);
    }
    return elements;
  } else {
    var capacity = 0;
    var size = 0;
    var jsElems = null;
    var elementClass = evidence$1.bk();
    capacity = 0;
    size = 0;
    var isCharArrayBuilder = (elementClass === $d_C.l());
    jsElems = [];
    var iterator$2 = it.c();
    while (iterator$2.k()) {
      var elem = iterator$2.f();
      var unboxedElem = (isCharArrayBuilder ? $uC(elem) : ((elem === null) ? $m_scm_ArrayBuilder$().j7(elementClass) : elem));
      jsElems.push(unboxedElem);
    }
    return $m_scm_ArrayBuilder$().j6(((elementClass === $d_V.l()) ? $d_jl_Void.l() : (((elementClass === $d_sr_Null$.l()) || (elementClass === $d_sr_Nothing$.l())) ? $d_O.l() : elementClass)), jsElems);
  }
});
$p.bT = (function(src, srcPos, dest, destPos, length) {
  var srcClass = $objectGetClass(src);
  if ((srcClass.J.Z && $objectGetClass(dest).J.R(srcClass.J))) {
    src.s(srcPos, dest, destPos, length);
  } else {
    $p_s_Array$__slowcopy__O__I__O__I__I__V(this, src, srcPos, dest, destPos, length);
  }
});
$p.qm = (function(original, newLength) {
  if ($isArrayOf_jl_Void(original, 1)) {
    return $p_s_Array$__newUnitArray__I__Ajl_Void(this, newLength);
  }
  if ((original instanceof $ac_O)) {
    return $m_ju_Arrays$().a9(original, newLength);
  }
  if ((original instanceof $ac_I)) {
    return $m_ju_Arrays$().o4(original, newLength);
  }
  if ((original instanceof $ac_D)) {
    return $m_ju_Arrays$().qn(original, newLength);
  }
  if ((original instanceof $ac_J)) {
    return $m_ju_Arrays$().o5(original, newLength);
  }
  if ((original instanceof $ac_F)) {
    return $m_ju_Arrays$().qo(original, newLength);
  }
  if ((original instanceof $ac_C)) {
    return $m_ju_Arrays$().o3(original, newLength);
  }
  if ((original instanceof $ac_B)) {
    return $m_ju_Arrays$().o2(original, newLength);
  }
  if ((original instanceof $ac_S)) {
    return $m_ju_Arrays$().o6(original, newLength);
  }
  if ((original instanceof $ac_Z)) {
    return $m_ju_Arrays$().o7(original, newLength);
  }
  throw new $c_s_MatchError(original);
});
$p.ql = (function(original, newLength, ct) {
  var runtimeClass = ct.bk();
  if (((runtimeClass !== null) && (runtimeClass === $d_V.l()))) {
    return $p_s_Array$__newUnitArray__I__Ajl_Void(this, newLength);
  } else if (runtimeClass.J.R($objectGetClass(original).J.Q().J)) {
    return (runtimeClass.J.X ? this.qm(original, newLength) : $m_ju_Arrays$().kv(original, newLength, $objectGetClass(runtimeClass.J.U(0))));
  } else {
    var dest = ct.bU(newLength);
    $m_s_Array$().bT(original, 0, dest, 0, $m_jl_reflect_Array$().b8(original));
    return dest;
  }
});
$p.og = (function(xs, ys) {
  if ((xs === ys)) {
    return true;
  }
  if ((xs.a.length !== ys.a.length)) {
    return false;
  }
  var len = xs.a.length;
  var i = 0;
  while ((i < len)) {
    if ((!$m_sr_BoxesRunTime$().n(xs.a[i], ys.a[i]))) {
      return false;
    }
    i = ((1 + i) | 0);
  }
  return true;
});
var $d_s_Array$ = new $TypeData().i($c_s_Array$, "scala.Array$", ({
  eD: 1
}));
var $n_s_Array$;
function $m_s_Array$() {
  if ((!$n_s_Array$)) {
    $n_s_Array$ = new $c_s_Array$();
  }
  return $n_s_Array$;
}
/** @constructor */
function $c_s_Array$EmptyArrays$() {
  this.gO = null;
  this.jA = null;
  $n_s_Array$EmptyArrays$ = this;
  this.gO = new $ac_I(0);
  this.jA = new $ac_O(0);
}
$p = $c_s_Array$EmptyArrays$.prototype = new $h_O();
$p.constructor = $c_s_Array$EmptyArrays$;
/** @constructor */
function $h_s_Array$EmptyArrays$() {
}
$h_s_Array$EmptyArrays$.prototype = $p;
var $d_s_Array$EmptyArrays$ = new $TypeData().i($c_s_Array$EmptyArrays$, "scala.Array$EmptyArrays$", ({
  eE: 1
}));
var $n_s_Array$EmptyArrays$;
function $m_s_Array$EmptyArrays$() {
  if ((!$n_s_Array$EmptyArrays$)) {
    $n_s_Array$EmptyArrays$ = new $c_s_Array$EmptyArrays$();
  }
  return $n_s_Array$EmptyArrays$;
}
var $d_s_Equals = new $TypeData().i(1, "scala.Equals", ({
  d: 1
}));
/** @constructor */
function $c_s_LowPriorityImplicits2() {
}
$p = $c_s_LowPriorityImplicits2.prototype = new $h_O();
$p.constructor = $c_s_LowPriorityImplicits2;
/** @constructor */
function $h_s_LowPriorityImplicits2() {
}
$h_s_LowPriorityImplicits2.prototype = $p;
/** @constructor */
function $c_s_Option$() {
}
$p = $c_s_Option$.prototype = new $h_O();
$p.constructor = $c_s_Option$;
/** @constructor */
function $h_s_Option$() {
}
$h_s_Option$.prototype = $p;
$p.hB = (function(x) {
  return ((x === null) ? $m_s_None$() : new $c_s_Some(x));
});
var $d_s_Option$ = new $TypeData().i($c_s_Option$, "scala.Option$", ({
  eM: 1
}));
var $n_s_Option$;
function $m_s_Option$() {
  if ((!$n_s_Option$)) {
    $n_s_Option$ = new $c_s_Option$();
  }
  return $n_s_Option$;
}
/** @constructor */
function $c_s_PartialFunction$() {
  this.gP = null;
  this.pf = null;
  this.mt = null;
  $n_s_PartialFunction$ = this;
  this.gP = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$2$2) => this.gP));
  this.pf = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$3$2) => false));
  this.mt = new $c_s_PartialFunction$$anon$1();
}
$p = $c_s_PartialFunction$.prototype = new $h_O();
$p.constructor = $c_s_PartialFunction$;
/** @constructor */
function $h_s_PartialFunction$() {
}
$h_s_PartialFunction$.prototype = $p;
$p.oL = (function(x) {
  return (this.gP === x);
});
var $d_s_PartialFunction$ = new $TypeData().i($c_s_PartialFunction$, "scala.PartialFunction$", ({
  eN: 1
}));
var $n_s_PartialFunction$;
function $m_s_PartialFunction$() {
  if ((!$n_s_PartialFunction$)) {
    $n_s_PartialFunction$ = new $c_s_PartialFunction$();
  }
  return $n_s_PartialFunction$;
}
function $f_s_Proxy__toString__T($thiz) {
  var $x_1 = $thiz;
  return ("" + $s_RTLong__toString__I__I__T($x_1.fy, $x_1.fz));
}
function $ps_sc_ArrayOps$__boxed$1__I__O__s_math_Ordering__O(len$1, \u03b4this$2, ord$1) {
  if ((len$1 < 300)) {
    var a = $m_sr_ScalaRunTime$().nU(\u03b4this$2);
    $m_s_util_Sorting$().l8(a, 0, $m_jl_reflect_Array$().b8(a), ord$1);
    return a;
  } else {
    if ($d_O.R($objectGetClass(\u03b4this$2).J.Q().J)) {
      var a$2 = $m_ju_Arrays$().kv(\u03b4this$2, len$1, $d_O.r().l());
    } else {
      var dest = new $ac_O(len$1);
      $m_s_Array$().bT(\u03b4this$2, 0, dest, 0, $m_jl_reflect_Array$().b8(\u03b4this$2));
      var a$2 = dest;
    }
    $m_ju_Arrays$().gz(a$2, ord$1);
    return $m_s_Array$().ql(a$2, len$1, $m_s_reflect_ClassTag$().kn($objectGetClass(\u03b4this$2).J.Q()));
  }
}
/** @constructor */
function $c_sc_ArrayOps$() {
  this.my = null;
  $n_sc_ArrayOps$ = this;
  this.my = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$2$2) => $m_sc_ArrayOps$().my));
}
$p = $c_sc_ArrayOps$.prototype = new $h_O();
$p.constructor = $c_sc_ArrayOps$;
/** @constructor */
function $h_sc_ArrayOps$() {
}
$h_sc_ArrayOps$.prototype = $p;
$p.sd = (function(this$, ord) {
  var len = $m_jl_reflect_Array$().b8(this$);
  if ((len <= 1)) {
    return $m_sr_ScalaRunTime$().nU(this$);
  } else if ((this$ instanceof $ac_O)) {
    var a = $m_ju_Arrays$().a9(this$, len);
    $m_ju_Arrays$().gz(a, ord);
    return a;
  } else if ((this$ instanceof $ac_I)) {
    if ((ord === $m_s_math_Ordering$Int$())) {
      var a$2 = $m_ju_Arrays$().o4(this$, len);
      $m_ju_Arrays$().oS(a$2);
      return a$2;
    } else {
      return $ps_sc_ArrayOps$__boxed$1__I__O__s_math_Ordering__O(len, this$, ord);
    }
  } else if ((this$ instanceof $ac_J)) {
    if ((ord === $m_s_math_Ordering$Long$())) {
      var a$3 = $m_ju_Arrays$().o5(this$, len);
      $m_ju_Arrays$().oT(a$3);
      return a$3;
    } else {
      return $ps_sc_ArrayOps$__boxed$1__I__O__s_math_Ordering__O(len, this$, ord);
    }
  } else if ((this$ instanceof $ac_C)) {
    if ((ord === $m_s_math_Ordering$Char$())) {
      var a$4 = $m_ju_Arrays$().o3(this$, len);
      $m_ju_Arrays$().oR(a$4);
      return a$4;
    } else {
      return $ps_sc_ArrayOps$__boxed$1__I__O__s_math_Ordering__O(len, this$, ord);
    }
  } else if ((this$ instanceof $ac_B)) {
    if ((ord === $m_s_math_Ordering$Byte$())) {
      var a$5 = $m_ju_Arrays$().o2(this$, len);
      $m_ju_Arrays$().oQ(a$5);
      return a$5;
    } else {
      return $ps_sc_ArrayOps$__boxed$1__I__O__s_math_Ordering__O(len, this$, ord);
    }
  } else if ((this$ instanceof $ac_S)) {
    if ((ord === $m_s_math_Ordering$Short$())) {
      var a$6 = $m_ju_Arrays$().o6(this$, len);
      $m_ju_Arrays$().oU(a$6);
      return a$6;
    } else {
      return $ps_sc_ArrayOps$__boxed$1__I__O__s_math_Ordering__O(len, this$, ord);
    }
  } else if ((this$ instanceof $ac_Z)) {
    if ((ord === $m_s_math_Ordering$Boolean$())) {
      var a$7 = $m_ju_Arrays$().o7(this$, len);
      $m_s_util_Sorting$().l8(a$7, 0, a$7.a.length, $m_s_math_Ordering$Boolean$());
      return a$7;
    } else {
      return $ps_sc_ArrayOps$__boxed$1__I__O__s_math_Ordering__O(len, this$, ord);
    }
  } else {
    return $ps_sc_ArrayOps$__boxed$1__I__O__s_math_Ordering__O(len, this$, ord);
  }
});
var $d_sc_ArrayOps$ = new $TypeData().i($c_sc_ArrayOps$, "scala.collection.ArrayOps$", ({
  eZ: 1
}));
var $n_sc_ArrayOps$;
function $m_sc_ArrayOps$() {
  if ((!$n_sc_ArrayOps$)) {
    $n_sc_ArrayOps$ = new $c_sc_ArrayOps$();
  }
  return $n_sc_ArrayOps$;
}
/** @constructor */
function $c_sc_Hashing$() {
}
$p = $c_sc_Hashing$.prototype = new $h_O();
$p.constructor = $c_sc_Hashing$;
/** @constructor */
function $h_sc_Hashing$() {
}
$h_sc_Hashing$.prototype = $p;
$p.bD = (function(hcode) {
  var h = ((hcode + (~(hcode << 9))) | 0);
  h = (h ^ ((h >>> 14) | 0));
  h = ((h + (h << 4)) | 0);
  return (h ^ ((h >>> 10) | 0));
});
var $d_sc_Hashing$ = new $TypeData().i($c_sc_Hashing$, "scala.collection.Hashing$", ({
  f4: 1
}));
var $n_sc_Hashing$;
function $m_sc_Hashing$() {
  if ((!$n_sc_Hashing$)) {
    $n_sc_Hashing$ = new $c_sc_Hashing$();
  }
  return $n_sc_Hashing$;
}
function $f_sc_IterableOnceOps__foreach__F1__V($thiz, f) {
  var it = $thiz.c();
  while (it.k()) {
    f.d(it.f());
  }
}
function $f_sc_IterableOnceOps__forall__F1__Z($thiz, p) {
  var res = true;
  var it = $thiz.c();
  while ((res && it.k())) {
    res = (!(!p.d(it.f())));
  }
  return res;
}
function $f_sc_IterableOnceOps__exists__F1__Z($thiz, p) {
  var res = false;
  var it = $thiz.c();
  while (((!res) && it.k())) {
    res = (!(!p.d(it.f())));
  }
  return res;
}
function $f_sc_IterableOnceOps__find__F1__s_Option($thiz, p) {
  var it = $thiz.c();
  while (it.k()) {
    var a = it.f();
    if ((!(!p.d(a)))) {
      return new $c_s_Some(a);
    }
  }
  return $m_s_None$();
}
function $f_sc_IterableOnceOps__foldLeft__O__F2__O($thiz, z, op) {
  if ($is_sc_IndexedSeq($thiz)) {
    return $p_sc_IterableOnceOps__loop$1__F2__sc_IndexedSeq__I__I__O__O($thiz, op, $thiz, 0, $thiz.l(), z);
  } else {
    var result = z;
    var it = $thiz.c();
    while (it.k()) {
      result = op.ca(result, it.f());
    }
    return result;
  }
}
function $f_sc_IterableOnceOps__isEmpty__Z($thiz) {
  var x30 = $thiz.q();
  if ((x30 === (-1))) {
    return (!$thiz.c().k());
  }
  if ((x30 === 0)) {
    return true;
  }
  return false;
}
function $f_sc_IterableOnceOps__copyToArray__O__I__I__I($thiz, dest, start, n) {
  var it = $thiz.c();
  var i = start;
  matchResult18: {
    var srclen;
    var x31 = $thiz.q();
    if ((x31 === (-1))) {
      var srclen = $m_jl_reflect_Array$().b8(dest);
      break matchResult18;
    }
    var srclen = x31;
  }
  var destLen = $m_jl_reflect_Array$().b8(dest);
  var limit = ((n < srclen) ? n : srclen);
  var capacity = ((start < 0) ? destLen : ((destLen - start) | 0));
  var total = ((capacity < limit) ? capacity : limit);
  var end = ((start + ((total < 0) ? 0 : total)) | 0);
  while (((i < end) && it.k())) {
    $m_sr_ScalaRunTime$().d3(dest, i, it.f());
    i = ((1 + i) | 0);
  }
  return ((i - start) | 0);
}
function $f_sc_IterableOnceOps__maxByOption__F1__s_math_Ordering__s_Option($thiz, f, ord) {
  return (($thiz.q() === 0) ? $m_s_None$() : $thiz.d4(new $c_sc_IterableOnceOps$Maximized("maxBy", f, new $c_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8(((x$2, y$2) => ord.dS(x$2, y$2)))), new $c_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8(((m$2, a$2) => m$2.nS(m$2, a$2)))).sl());
}
function $f_sc_IterableOnceOps__mkString__T__T__T__T($thiz, start, sep, end) {
  return (($thiz.q() === 0) ? (("" + start) + end) : $thiz.cl($ct_scm_StringBuilder__(new $c_scm_StringBuilder()), start, sep, end).bC.w);
}
function $f_sc_IterableOnceOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder($thiz, b, start, sep, end) {
  var jsb = b.bC;
  if ((start.length !== 0)) {
    jsb.w = (("" + jsb.w) + start);
  }
  var it = $thiz.c();
  if (it.k()) {
    var obj = it.f();
    jsb.w = (("" + jsb.w) + obj);
    while (it.k()) {
      if ((sep.length !== 0)) {
        jsb.w = (("" + jsb.w) + sep);
      }
      var obj$1 = it.f();
      jsb.w = (("" + jsb.w) + obj$1);
    }
  }
  if ((end.length !== 0)) {
    jsb.w = (("" + jsb.w) + end);
  }
  return b;
}
function $f_sc_IterableOnceOps__toArray__s_reflect_ClassTag__O($thiz, evidence$1) {
  if (($thiz.q() >= 0)) {
    var length = $thiz.q();
    var destination = evidence$1.bU(length);
    $thiz.bc(destination, 0, 2147483647);
    return destination;
  } else {
    var capacity = 0;
    var size = 0;
    var jsElems = null;
    var elementClass = evidence$1.bk();
    capacity = 0;
    size = 0;
    var isCharArrayBuilder = (elementClass === $d_C.l());
    jsElems = [];
    var it = $thiz.c();
    while (it.k()) {
      var elem = it.f();
      var unboxedElem = (isCharArrayBuilder ? $uC(elem) : ((elem === null) ? $m_scm_ArrayBuilder$().j7(elementClass) : elem));
      jsElems.push(unboxedElem);
    }
    return $m_scm_ArrayBuilder$().j6(((elementClass === $d_V.l()) ? $d_jl_Void.l() : (((elementClass === $d_sr_Null$.l()) || (elementClass === $d_sr_Nothing$.l())) ? $d_O.l() : elementClass)), jsElems);
  }
}
function $f_sc_IterableOnceOps__reversed__sc_Iterable($thiz) {
  var xs = $m_sci_Nil$();
  var it = $thiz.c();
  while (it.k()) {
    xs = new $c_sci_$colon$colon(it.f(), xs);
  }
  return xs;
}
function $p_sc_IterableOnceOps__loop$1__F2__sc_IndexedSeq__I__I__O__O($thiz, op$1, seq$1, at, end, acc) {
  var acc$tailLocal1 = acc;
  var at$tailLocal1 = at;
  while (true) {
    if ((at$tailLocal1 === end)) {
      return acc$tailLocal1;
    } else {
      var at$tailLocal1$tmp1 = ((1 + at$tailLocal1) | 0);
      var acc$tailLocal1$tmp1 = op$1.ca(acc$tailLocal1, seq$1.r(at$tailLocal1));
      at$tailLocal1 = at$tailLocal1$tmp1;
      acc$tailLocal1 = acc$tailLocal1$tmp1;
    }
  }
}
/** @constructor */
function $c_sc_Iterator$ConcatIteratorCell(head, tail) {
  this.mH = null;
  this.gV = null;
  this.mH = head;
  this.gV = tail;
}
$p = $c_sc_Iterator$ConcatIteratorCell.prototype = new $h_O();
$p.constructor = $c_sc_Iterator$ConcatIteratorCell;
/** @constructor */
function $h_sc_Iterator$ConcatIteratorCell() {
}
$h_sc_Iterator$ConcatIteratorCell.prototype = $p;
$p.ra = (function() {
  return this.mH.a2().c();
});
var $d_sc_Iterator$ConcatIteratorCell = new $TypeData().i($c_sc_Iterator$ConcatIteratorCell, "scala.collection.Iterator$ConcatIteratorCell", ({
  fh: 1
}));
/** @constructor */
function $c_sc_StringOps$() {
  this.mO = null;
  $n_sc_StringOps$ = this;
  this.mO = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$1$2) => this.mO));
}
$p = $c_sc_StringOps$.prototype = new $h_O();
$p.constructor = $c_sc_StringOps$;
/** @constructor */
function $h_sc_StringOps$() {
}
$h_sc_StringOps$.prototype = $p;
$p.l6 = (function(this$, from, until) {
  var start = ((from > 0) ? from : 0);
  var that = this$.length;
  var end = ((until < that) ? until : that);
  return ((start >= end) ? "" : this$.substring(start, end));
});
$p.j3 = (function(this$) {
  return $ct_s_util_matching_Regex__T__sci_Seq__(new $c_s_util_matching_Regex(), this$, new $c_sjsr_WrappedVarArgs([]));
});
$p.rT = (function(this$, arg) {
  return (false ? arg.sC() : arg);
});
$p.qS = (function(this$, args) {
  return $m_jl_String$().qQ(this$, $m_sr_Arrays$().s0(args.aa(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((arg$2) => this.rT(this$, arg$2)))), $d_O.l()));
});
$p.r9 = (function(this$) {
  if ((this$ === "")) {
    throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), "head of empty String");
  } else {
    return this$.charCodeAt(0);
  }
});
$p.j9 = (function(this$) {
  if ((this$ === "")) {
    throw new $c_jl_UnsupportedOperationException("tail of empty String");
  } else {
    return this.l6(this$, 1, this$.length);
  }
});
$p.oZ = (function(this$, n) {
  var y = this$.length;
  return this.l6(this$, 0, ((n < y) ? n : y));
});
$p.o8 = (function(this$, n) {
  var y = this$.length;
  return this.l6(this$, ((n < y) ? n : y), this$.length);
});
var $d_sc_StringOps$ = new $TypeData().i($c_sc_StringOps$, "scala.collection.StringOps$", ({
  ft: 1
}));
var $n_sc_StringOps$;
function $m_sc_StringOps$() {
  if ((!$n_sc_StringOps$)) {
    $n_sc_StringOps$ = new $c_sc_StringOps$();
  }
  return $n_sc_StringOps$;
}
/** @constructor */
function $c_scg_CommonErrors$() {
}
$p = $c_scg_CommonErrors$.prototype = new $h_O();
$p.constructor = $c_scg_CommonErrors$;
/** @constructor */
function $h_scg_CommonErrors$() {
}
$h_scg_CommonErrors$.prototype = $p;
$p.kQ = (function(index, max) {
  return $ct_jl_IndexOutOfBoundsException__T__(new $c_jl_IndexOutOfBoundsException(), (((index + " is out of bounds (min 0, max ") + max) + ")"));
});
var $d_scg_CommonErrors$ = new $TypeData().i($c_scg_CommonErrors$, "scala.collection.generic.CommonErrors$", ({
  fA: 1
}));
var $n_scg_CommonErrors$;
function $m_scg_CommonErrors$() {
  if ((!$n_scg_CommonErrors$)) {
    $n_scg_CommonErrors$ = new $c_scg_CommonErrors$();
  }
  return $n_scg_CommonErrors$;
}
/** @constructor */
function $c_sci_IndexedSeqDefaults$() {
  this.mT = 0;
  $n_sci_IndexedSeqDefaults$ = this;
  try {
    $m_sc_StringOps$();
    var $x_1 = $m_jl_Integer$().kU($m_jl_System$SystemProperties$().kO("scala.collection.immutable.IndexedSeq.defaultApplyPreferredMaxLength", "64"), 10, 214748364);
  } catch (e) {
    if (false) {
      var $x_1 = 64;
    } else {
      var $x_1;
      throw e;
    }
  }
  this.mT = $x_1;
}
$p = $c_sci_IndexedSeqDefaults$.prototype = new $h_O();
$p.constructor = $c_sci_IndexedSeqDefaults$;
/** @constructor */
function $h_sci_IndexedSeqDefaults$() {
}
$h_sci_IndexedSeqDefaults$.prototype = $p;
var $d_sci_IndexedSeqDefaults$ = new $TypeData().i($c_sci_IndexedSeqDefaults$, "scala.collection.immutable.IndexedSeqDefaults$", ({
  fL: 1
}));
var $n_sci_IndexedSeqDefaults$;
function $m_sci_IndexedSeqDefaults$() {
  if ((!$n_sci_IndexedSeqDefaults$)) {
    $n_sci_IndexedSeqDefaults$ = new $c_sci_IndexedSeqDefaults$();
  }
  return $n_sci_IndexedSeqDefaults$;
}
/** @constructor */
function $c_sci_LazyList$EmptyMarker$() {
}
$p = $c_sci_LazyList$EmptyMarker$.prototype = new $h_O();
$p.constructor = $c_sci_LazyList$EmptyMarker$;
/** @constructor */
function $h_sci_LazyList$EmptyMarker$() {
}
$h_sci_LazyList$EmptyMarker$.prototype = $p;
var $d_sci_LazyList$EmptyMarker$ = new $TypeData().i($c_sci_LazyList$EmptyMarker$, "scala.collection.immutable.LazyList$EmptyMarker$", ({
  fO: 1
}));
var $n_sci_LazyList$EmptyMarker$;
function $m_sci_LazyList$EmptyMarker$() {
  if ((!$n_sci_LazyList$EmptyMarker$)) {
    $n_sci_LazyList$EmptyMarker$ = new $c_sci_LazyList$EmptyMarker$();
  }
  return $n_sci_LazyList$EmptyMarker$;
}
/** @constructor */
function $c_sci_LazyList$LazyBuilder$DeferredState() {
  this.jQ = null;
}
$p = $c_sci_LazyList$LazyBuilder$DeferredState.prototype = new $h_O();
$p.constructor = $c_sci_LazyList$LazyBuilder$DeferredState;
/** @constructor */
function $h_sci_LazyList$LazyBuilder$DeferredState() {
}
$h_sci_LazyList$LazyBuilder$DeferredState.prototype = $p;
$p.kA = (function() {
  var state = this.jQ;
  if ((state === null)) {
    throw $ct_jl_IllegalStateException__T__(new $c_jl_IllegalStateException(), "uninitialized");
  }
  return state.a2();
});
$p.kR = (function(state) {
  if ((this.jQ !== null)) {
    throw $ct_jl_IllegalStateException__T__(new $c_jl_IllegalStateException(), "already initialized");
  }
  this.jQ = state;
});
var $d_sci_LazyList$LazyBuilder$DeferredState = new $TypeData().i($c_sci_LazyList$LazyBuilder$DeferredState, "scala.collection.immutable.LazyList$LazyBuilder$DeferredState", ({
  fQ: 1
}));
/** @constructor */
function $c_sci_LazyList$MidEvaluation$() {
}
$p = $c_sci_LazyList$MidEvaluation$.prototype = new $h_O();
$p.constructor = $c_sci_LazyList$MidEvaluation$;
/** @constructor */
function $h_sci_LazyList$MidEvaluation$() {
}
$h_sci_LazyList$MidEvaluation$.prototype = $p;
var $d_sci_LazyList$MidEvaluation$ = new $TypeData().i($c_sci_LazyList$MidEvaluation$, "scala.collection.immutable.LazyList$MidEvaluation$", ({
  fS: 1
}));
var $n_sci_LazyList$MidEvaluation$;
function $m_sci_LazyList$MidEvaluation$() {
  if ((!$n_sci_LazyList$MidEvaluation$)) {
    $n_sci_LazyList$MidEvaluation$ = new $c_sci_LazyList$MidEvaluation$();
  }
  return $n_sci_LazyList$MidEvaluation$;
}
/** @constructor */
function $c_sci_MapNode$() {
  this.mW = null;
  $n_sci_MapNode$ = this;
  $m_s_reflect_ManifestFactory$IntManifest$();
  this.mW = new $c_sci_BitmapIndexedMapNode(0, 0, new $ac_O(0), new $ac_I(0), 0, 0);
}
$p = $c_sci_MapNode$.prototype = new $h_O();
$p.constructor = $c_sci_MapNode$;
/** @constructor */
function $h_sci_MapNode$() {
}
$h_sci_MapNode$.prototype = $p;
var $d_sci_MapNode$ = new $TypeData().i($c_sci_MapNode$, "scala.collection.immutable.MapNode$", ({
  g9: 1
}));
var $n_sci_MapNode$;
function $m_sci_MapNode$() {
  if ((!$n_sci_MapNode$)) {
    $n_sci_MapNode$ = new $c_sci_MapNode$();
  }
  return $n_sci_MapNode$;
}
function $p_sci_Node__arrayIndexOutOfBounds__O__I__jl_ArrayIndexOutOfBoundsException($thiz, as, ix) {
  return $ct_jl_ArrayIndexOutOfBoundsException__T__(new $c_jl_ArrayIndexOutOfBoundsException(), ((ix + " is out of bounds (min 0, max ") + (($m_jl_reflect_Array$().b8(as) - 1) | 0)));
}
/** @constructor */
function $c_sci_Node() {
}
$p = $c_sci_Node.prototype = new $h_O();
$p.constructor = $c_sci_Node;
/** @constructor */
function $h_sci_Node() {
}
$h_sci_Node.prototype = $p;
$p.j4 = (function(as, ix) {
  if ((ix < 0)) {
    throw $p_sci_Node__arrayIndexOutOfBounds__O__I__jl_ArrayIndexOutOfBoundsException(this, as, ix);
  }
  if ((ix > ((as.a.length - 1) | 0))) {
    throw $p_sci_Node__arrayIndexOutOfBounds__O__I__jl_ArrayIndexOutOfBoundsException(this, as, ix);
  }
  var result = new $ac_I(((as.a.length - 1) | 0));
  as.s(0, result, 0, ix);
  var srcPos = ((1 + ix) | 0);
  var length = ((((as.a.length - ix) | 0) - 1) | 0);
  as.s(srcPos, result, ix, length);
  return result;
});
$p.ot = (function(as, ix, elem) {
  if ((ix < 0)) {
    throw $p_sci_Node__arrayIndexOutOfBounds__O__I__jl_ArrayIndexOutOfBoundsException(this, as, ix);
  }
  if ((ix > as.a.length)) {
    throw $p_sci_Node__arrayIndexOutOfBounds__O__I__jl_ArrayIndexOutOfBoundsException(this, as, ix);
  }
  var result = new $ac_I(((1 + as.a.length) | 0));
  as.s(0, result, 0, ix);
  result.a[ix] = elem;
  var destPos = ((1 + ix) | 0);
  var length = ((as.a.length - ix) | 0);
  as.s(ix, result, destPos, length);
  return result;
});
var $d_sci_Node = new $TypeData().i(0, "scala.collection.immutable.Node", ({
  aF: 1
}));
/** @constructor */
function $c_sci_Node$() {
  this.h7 = 0;
  $n_sci_Node$ = this;
  this.h7 = $doubleToInt((+Math.ceil(6.4)));
}
$p = $c_sci_Node$.prototype = new $h_O();
$p.constructor = $c_sci_Node$;
/** @constructor */
function $h_sci_Node$() {
}
$h_sci_Node$.prototype = $p;
$p.cH = (function(hash, shift) {
  return (31 & ((hash >>> shift) | 0));
});
$p.bZ = (function(mask) {
  return (1 << mask);
});
$p.fF = (function(bitmap, bitpos) {
  return $m_jl_Integer$().bR((bitmap & ((bitpos - 1) | 0)));
});
$p.bL = (function(bitmap, mask, bitpos) {
  return ((bitmap === (-1)) ? mask : this.fF(bitmap, bitpos));
});
var $d_sci_Node$ = new $TypeData().i($c_sci_Node$, "scala.collection.immutable.Node$", ({
  gd: 1
}));
var $n_sci_Node$;
function $m_sci_Node$() {
  if ((!$n_sci_Node$)) {
    $n_sci_Node$ = new $c_sci_Node$();
  }
  return $n_sci_Node$;
}
/** @constructor */
function $c_sci_SetNode$() {
  this.n1 = null;
  $n_sci_SetNode$ = this;
  $m_s_reflect_ManifestFactory$IntManifest$();
  this.n1 = new $c_sci_BitmapIndexedSetNode(0, 0, new $ac_O(0), new $ac_I(0), 0, 0);
}
$p = $c_sci_SetNode$.prototype = new $h_O();
$p.constructor = $c_sci_SetNode$;
/** @constructor */
function $h_sci_SetNode$() {
}
$h_sci_SetNode$.prototype = $p;
var $d_sci_SetNode$ = new $TypeData().i($c_sci_SetNode$, "scala.collection.immutable.SetNode$", ({
  gr: 1
}));
var $n_sci_SetNode$;
function $m_sci_SetNode$() {
  if ((!$n_sci_SetNode$)) {
    $n_sci_SetNode$ = new $c_sci_SetNode$();
  }
  return $n_sci_SetNode$;
}
/** @constructor */
function $c_sci_VectorStatics$() {
  this.jV = null;
  this.ao = null;
  this.by = null;
  this.dh = null;
  this.h9 = null;
  this.jW = null;
  $n_sci_VectorStatics$ = this;
  this.jV = new $ac_O(0);
  this.ao = new ($d_O.r().r().C)(0);
  this.by = new ($d_O.r().r().r().C)(0);
  this.dh = new ($d_O.r().r().r().r().C)(0);
  this.h9 = new ($d_O.r().r().r().r().r().C)(0);
  this.jW = new ($d_O.r().r().r().r().r().r().C)(0);
}
$p = $c_sci_VectorStatics$.prototype = new $h_O();
$p.constructor = $c_sci_VectorStatics$;
/** @constructor */
function $h_sci_VectorStatics$() {
}
$h_sci_VectorStatics$.prototype = $p;
$p.go = (function(a, elem) {
  var alen = a.a.length;
  var ac = new $ac_O(((1 + alen) | 0));
  a.s(0, ac, 0, alen);
  ac.a[alen] = elem;
  return ac;
});
$p.F = (function(a, elem) {
  var ac = $m_ju_Arrays$().a9(a, ((1 + a.a.length) | 0));
  ac.a[((ac.a.length - 1) | 0)] = elem;
  return ac;
});
$p.gp = (function(elem, a) {
  var ac = new $ac_O(((1 + a.a.length) | 0));
  var length = a.a.length;
  a.s(0, ac, 1, length);
  ac.a[0] = elem;
  return ac;
});
$p.B = (function(elem, a) {
  var ac = $objectGetClass(a).J.Q().J.U(((1 + a.a.length) | 0));
  var length$1 = a.a.length;
  a.s(0, ac, 1, length$1);
  ac.a[0] = elem;
  return ac;
});
$p.kE = (function(level, a, f) {
  var i = 0;
  var len = a.a.length;
  if ((level === 0)) {
    while ((i < len)) {
      f.d(a.a[i]);
      i = ((1 + i) | 0);
    }
  } else {
    var l = ((level - 1) | 0);
    while ((i < len)) {
      this.kE(l, a.a[i], f);
      i = ((1 + i) | 0);
    }
  }
});
$p.d5 = (function(a, f) {
  var i = 0;
  while ((i < a.a.length)) {
    var v1 = a.a[i];
    var v2 = f.d(v1);
    if ((!Object.is(v1, v2))) {
      return this.rs(a, f, i, v2);
    }
    i = ((1 + i) | 0);
  }
  return a;
});
$p.rs = (function(a, f, at, v2) {
  var ac = new $ac_O(a.a.length);
  if ((at > 0)) {
    a.s(0, ac, 0, at);
  }
  ac.a[at] = v2;
  var i = ((1 + at) | 0);
  while ((i < a.a.length)) {
    ac.a[i] = f.d(a.a[i]);
    i = ((1 + i) | 0);
  }
  return ac;
});
$p.aJ = (function(n, a, f) {
  if ((n === 1)) {
    return this.d5(a, f);
  } else {
    var i = 0;
    while ((i < a.a.length)) {
      var v1 = a.a[i];
      var v2 = this.aJ(((n - 1) | 0), v1, f);
      if ((v1 !== v2)) {
        return this.rt(n, a, f, i, v2);
      }
      i = ((1 + i) | 0);
    }
    return a;
  }
});
$p.rt = (function(n, a, f, at, v2) {
  var ac = $objectGetClass(a).J.Q().J.U(a.a.length);
  if ((at > 0)) {
    a.s(0, ac, 0, at);
  }
  ac.a[at] = v2;
  var i = ((1 + at) | 0);
  while ((i < a.a.length)) {
    ac.a[i] = this.aJ(((n - 1) | 0), a.a[i], f);
    i = ((1 + i) | 0);
  }
  return ac;
});
var $d_sci_VectorStatics$ = new $TypeData().i($c_sci_VectorStatics$, "scala.collection.immutable.VectorStatics$", ({
  gF: 1
}));
var $n_sci_VectorStatics$;
function $m_sci_VectorStatics$() {
  if ((!$n_sci_VectorStatics$)) {
    $n_sci_VectorStatics$ = new $c_sci_VectorStatics$();
  }
  return $n_sci_VectorStatics$;
}
/** @constructor */
function $c_scm_ArrayBuilder$() {
}
$p = $c_scm_ArrayBuilder$.prototype = new $h_O();
$p.constructor = $c_scm_ArrayBuilder$;
/** @constructor */
function $h_scm_ArrayBuilder$() {
}
$h_scm_ArrayBuilder$.prototype = $p;
$p.j7 = (function(runtimeClass) {
  return ((runtimeClass === $d_B.l()) ? 0 : ((runtimeClass === $d_S.l()) ? 0 : ((runtimeClass === $d_C.l()) ? 0 : ((runtimeClass === $d_I.l()) ? 0 : ((runtimeClass === $d_J.l()) ? $bL(0, 0) : ((runtimeClass === $d_F.l()) ? 0.0 : ((runtimeClass === $d_D.l()) ? 0.0 : ((runtimeClass === $d_Z.l()) ? false : ((runtimeClass === $d_V.l()) ? (void 0) : null)))))))));
});
$p.j6 = (function(runtimeClass, a) {
  var len = (a.length | 0);
  if ((runtimeClass === $d_C.l())) {
    var result = new $ac_C(len);
    var i = 0;
    while ((i !== len)) {
      result.a[i] = (65535 & (a[i] | 0));
      i = ((1 + i) | 0);
    }
    return result;
  } else {
    var result$2 = runtimeClass.J.U(len);
    var i$2 = 0;
    while ((i$2 !== len)) {
      $m_sr_ScalaRunTime$().d3(result$2, i$2, a[i$2]);
      i$2 = ((1 + i$2) | 0);
    }
    return result$2;
  }
});
var $d_scm_ArrayBuilder$ = new $TypeData().i($c_scm_ArrayBuilder$, "scala.collection.mutable.ArrayBuilder$", ({
  gN: 1
}));
var $n_scm_ArrayBuilder$;
function $m_scm_ArrayBuilder$() {
  if ((!$n_scm_ArrayBuilder$)) {
    $n_scm_ArrayBuilder$ = new $c_scm_ArrayBuilder$();
  }
  return $n_scm_ArrayBuilder$;
}
/** @constructor */
function $c_scm_HashMap$Node(_key, _hash, _value, _next) {
  this.eT = null;
  this.dK = 0;
  this.cC = null;
  this.bh = null;
  this.eT = _key;
  this.dK = _hash;
  this.cC = _value;
  this.bh = _next;
}
$p = $c_scm_HashMap$Node.prototype = new $h_O();
$p.constructor = $c_scm_HashMap$Node;
/** @constructor */
function $h_scm_HashMap$Node() {
}
$h_scm_HashMap$Node.prototype = $p;
$p.hH = (function(k, h) {
  var \u03b4this$tailLocal1 = this;
  while (true) {
    if (((h === \u03b4this$tailLocal1.dK) && $m_sr_BoxesRunTime$().n(k, \u03b4this$tailLocal1.eT))) {
      return \u03b4this$tailLocal1;
    } else if (((\u03b4this$tailLocal1.bh === null) || (\u03b4this$tailLocal1.dK > h))) {
      return null;
    } else {
      \u03b4this$tailLocal1 = \u03b4this$tailLocal1.bh;
    }
  }
});
$p.dm = (function(f) {
  var \u03b4this$tailLocal3 = this;
  while (true) {
    f.ca(\u03b4this$tailLocal3.eT, \u03b4this$tailLocal3.cC);
    if ((\u03b4this$tailLocal3.bh !== null)) {
      \u03b4this$tailLocal3 = \u03b4this$tailLocal3.bh;
    } else {
      return (void 0);
    }
  }
});
$p.t = (function() {
  return ((((((("Node(" + this.eT) + ", ") + this.cC) + ", ") + this.dK) + ") -> ") + this.bh);
});
var $d_scm_HashMap$Node = new $TypeData().i($c_scm_HashMap$Node, "scala.collection.mutable.HashMap$Node", ({
  h1: 1
}));
/** @constructor */
function $c_scm_HashSet$Node(_key, _hash, _next) {
  this.gk = null;
  this.eh = 0;
  this.bB = null;
  this.gk = _key;
  this.eh = _hash;
  this.bB = _next;
}
$p = $c_scm_HashSet$Node.prototype = new $h_O();
$p.constructor = $c_scm_HashSet$Node;
/** @constructor */
function $h_scm_HashSet$Node() {
}
$h_scm_HashSet$Node.prototype = $p;
$p.qL = (function(k, h) {
  var \u03b4this$tailLocal1 = this;
  while (true) {
    if (((h === \u03b4this$tailLocal1.eh) && $m_sr_BoxesRunTime$().n(k, \u03b4this$tailLocal1.gk))) {
      return \u03b4this$tailLocal1;
    } else if (((\u03b4this$tailLocal1.bB === null) || (\u03b4this$tailLocal1.eh > h))) {
      return null;
    } else {
      \u03b4this$tailLocal1 = \u03b4this$tailLocal1.bB;
    }
  }
});
$p.t = (function() {
  return ((((("Node(" + this.gk) + ", ") + this.eh) + ") -> ") + this.bB);
});
var $d_scm_HashSet$Node = new $TypeData().i($c_scm_HashSet$Node, "scala.collection.mutable.HashSet$Node", ({
  h7: 1
}));
/** @constructor */
function $c_scm_MutationTracker$() {
}
$p = $c_scm_MutationTracker$.prototype = new $h_O();
$p.constructor = $c_scm_MutationTracker$;
/** @constructor */
function $h_scm_MutationTracker$() {
}
$h_scm_MutationTracker$.prototype = $p;
$p.kp = (function(expectedCount, actualCount, message) {
  if ((actualCount !== expectedCount)) {
    throw new $c_ju_ConcurrentModificationException(message);
  }
});
var $d_scm_MutationTracker$ = new $TypeData().i($c_scm_MutationTracker$, "scala.collection.mutable.MutationTracker$", ({
  hg: 1
}));
var $n_scm_MutationTracker$;
function $m_scm_MutationTracker$() {
  if ((!$n_scm_MutationTracker$)) {
    $n_scm_MutationTracker$ = new $c_scm_MutationTracker$();
  }
  return $n_scm_MutationTracker$;
}
function $ct_s_concurrent_BatchingExecutor$AbstractBatch__jl_Runnable__Ajl_Runnable__I__($thiz, first, other, size) {
  $thiz.hh = first;
  $thiz.hi = other;
  $thiz.eW = size;
  return $thiz;
}
function $p_s_concurrent_BatchingExecutor$AbstractBatch__ensureCapacity__I__Ajl_Runnable($thiz, curSize) {
  var curOther = $thiz.hi;
  var curLen = curOther.a.length;
  if ((curSize <= curLen)) {
    return curOther;
  } else {
    var newLen = ((curLen === 0) ? 4 : (curLen << 1));
    if ((newLen <= curLen)) {
      throw new $c_jl_StackOverflowError(("Space limit of asynchronous stack reached: " + curLen));
    }
    var newOther = new ($d_jl_Runnable.r().C)(newLen);
    curOther.s(0, newOther, 0, curLen);
    $thiz.hi = newOther;
    return newOther;
  }
}
/** @constructor */
function $c_s_concurrent_BatchingExecutor$AbstractBatch() {
  this.hh = null;
  this.hi = null;
  this.eW = 0;
}
$p = $c_s_concurrent_BatchingExecutor$AbstractBatch.prototype = new $h_O();
$p.constructor = $c_s_concurrent_BatchingExecutor$AbstractBatch;
/** @constructor */
function $h_s_concurrent_BatchingExecutor$AbstractBatch() {
}
$h_s_concurrent_BatchingExecutor$AbstractBatch.prototype = $p;
$p.rI = (function(r) {
  var sz = this.eW;
  if ((sz === 0)) {
    this.hh = r;
  } else {
    $p_s_concurrent_BatchingExecutor$AbstractBatch__ensureCapacity__I__Ajl_Runnable(this, sz).a[((sz - 1) | 0)] = r;
  }
  this.eW = ((1 + sz) | 0);
});
$p.rS = (function(n) {
  var n$tailLocal1 = n;
  while (true) {
    if ((n$tailLocal1 > 0)) {
      var x1 = this.eW;
      if ((x1 === 0)) {
        return (void 0);
      }
      if ((x1 === 1)) {
        var x$proxy1 = this.hh;
        if ((x$proxy1 === null)) {
          $m_sr_Scala3RunTime$().bV();
        }
        this.hh = null;
        this.eW = 0;
        x$proxy1.fJ();
        n$tailLocal1 = ((n$tailLocal1 - 1) | 0);
        continue;
      }
      var o = this.hi;
      var x$proxy2 = o.a[((x1 - 2) | 0)];
      if ((x$proxy2 === null)) {
        $m_sr_Scala3RunTime$().bV();
      }
      o.a[((x1 - 2) | 0)] = null;
      this.eW = ((x1 - 1) | 0);
      x$proxy2.fJ();
      n$tailLocal1 = ((n$tailLocal1 - 1) | 0);
    } else {
      return (void 0);
    }
  }
});
/** @constructor */
function $c_s_concurrent_BatchingExecutorStatics$() {
  this.ng = null;
  $n_s_concurrent_BatchingExecutorStatics$ = this;
  this.ng = new ($d_jl_Runnable.r().C)(0);
}
$p = $c_s_concurrent_BatchingExecutorStatics$.prototype = new $h_O();
$p.constructor = $c_s_concurrent_BatchingExecutorStatics$;
/** @constructor */
function $h_s_concurrent_BatchingExecutorStatics$() {
}
$h_s_concurrent_BatchingExecutorStatics$.prototype = $p;
var $d_s_concurrent_BatchingExecutorStatics$ = new $TypeData().i($c_s_concurrent_BatchingExecutorStatics$, "scala.concurrent.BatchingExecutorStatics$", ({
  hn: 1
}));
var $n_s_concurrent_BatchingExecutorStatics$;
function $m_s_concurrent_BatchingExecutorStatics$() {
  if ((!$n_s_concurrent_BatchingExecutorStatics$)) {
    $n_s_concurrent_BatchingExecutorStatics$ = new $c_s_concurrent_BatchingExecutorStatics$();
  }
  return $n_s_concurrent_BatchingExecutorStatics$;
}
/** @constructor */
function $c_s_concurrent_ExecutionContext$() {
  this.nh = null;
  this.ni = false;
  this.gl = null;
  $n_s_concurrent_ExecutionContext$ = this;
  this.gl = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$1$3) => {
    _$1$3.hR($m_jl_System$Streams$().g3);
  }));
}
$p = $c_s_concurrent_ExecutionContext$.prototype = new $h_O();
$p.constructor = $c_s_concurrent_ExecutionContext$;
/** @constructor */
function $h_s_concurrent_ExecutionContext$() {
}
$h_s_concurrent_ExecutionContext$.prototype = $p;
$p.iX = (function() {
  if ((!this.ni)) {
    this.nh = $m_sjs_concurrent_JSExecutionContext$().ny;
    this.ni = true;
  }
  return this.nh;
});
var $d_s_concurrent_ExecutionContext$ = new $TypeData().i($c_s_concurrent_ExecutionContext$, "scala.concurrent.ExecutionContext$", ({
  ho: 1
}));
var $n_s_concurrent_ExecutionContext$;
function $m_s_concurrent_ExecutionContext$() {
  if ((!$n_s_concurrent_ExecutionContext$)) {
    $n_s_concurrent_ExecutionContext$ = new $c_s_concurrent_ExecutionContext$();
  }
  return $n_s_concurrent_ExecutionContext$;
}
/** @constructor */
function $c_s_concurrent_Future$() {
  this.nk = null;
  this.nm = null;
  this.nl = null;
  this.k7 = null;
  this.nn = null;
  this.no = null;
  $n_s_concurrent_Future$ = this;
  $m_sci_Map$().hI(new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), $d_Z.l(), $d_jl_Boolean.l()), $ct_T2__O__O__(new $c_T2(), $d_B.l(), $d_jl_Byte.l()), $ct_T2__O__O__(new $c_T2(), $d_C.l(), $d_jl_Character.l()), $ct_T2__O__O__(new $c_T2(), $d_S.l(), $d_jl_Short.l()), $ct_T2__O__O__(new $c_T2(), $d_I.l(), $d_jl_Integer.l()), $ct_T2__O__O__(new $c_T2(), $d_J.l(), $d_jl_Long.l()), $ct_T2__O__O__(new $c_T2(), $d_F.l(), $d_jl_Float.l()), $ct_T2__O__O__(new $c_T2(), $d_D.l(), $d_jl_Double.l()), $ct_T2__O__O__(new $c_T2(), $d_V.l(), $d_jl_Void.l())]));
  this.nk = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((t$2) => {
    throw new $c_s_concurrent_Future$$anon$1(t$2);
  }));
  this.nm = new $c_s_util_Failure(new $c_s_concurrent_Future$$anon$2());
  this.nl = new $c_s_util_Failure(new $c_s_concurrent_Future$$anon$3());
  $m_s_concurrent_Future$().oo(this.nl);
  this.k7 = $m_s_concurrent_Future$().qG(new $c_s_concurrent_Future$$anon$4());
  this.nn = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((t$2$1) => this.k7));
  this.no = this.oo(new $c_s_util_Success((void 0)));
}
$p = $c_s_concurrent_Future$.prototype = new $h_O();
$p.constructor = $c_s_concurrent_Future$;
/** @constructor */
function $h_s_concurrent_Future$() {
}
$h_s_concurrent_Future$.prototype = $p;
$p.qG = (function(exception) {
  return $m_s_concurrent_Promise$().qH(exception);
});
$p.oo = (function(result) {
  return $ct_s_concurrent_impl_Promise$DefaultPromise__s_util_Try__(new $c_s_concurrent_impl_Promise$DefaultPromise(), result);
});
$p.q3 = (function(body, executor) {
  return this.no.oz(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$4$2) => body.a2())), executor);
});
var $d_s_concurrent_Future$ = new $TypeData().i($c_s_concurrent_Future$, "scala.concurrent.Future$", ({
  hq: 1
}));
var $n_s_concurrent_Future$;
function $m_s_concurrent_Future$() {
  if ((!$n_s_concurrent_Future$)) {
    $n_s_concurrent_Future$ = new $c_s_concurrent_Future$();
  }
  return $n_s_concurrent_Future$;
}
function $f_s_concurrent_Promise__complete__s_util_Try__s_concurrent_Promise($thiz, result) {
  if ($thiz.sm(result)) {
    return $thiz;
  } else {
    throw $ct_jl_IllegalStateException__T__(new $c_jl_IllegalStateException(), "Promise already completed.");
  }
}
function $f_s_concurrent_Promise__success__O__s_concurrent_Promise($thiz, value) {
  return $f_s_concurrent_Promise__complete__s_util_Try__s_concurrent_Promise($thiz, new $c_s_util_Success(value));
}
function $f_s_concurrent_Promise__failure__jl_Throwable__s_concurrent_Promise($thiz, cause) {
  return $f_s_concurrent_Promise__complete__s_util_Try__s_concurrent_Promise($thiz, new $c_s_util_Failure(cause));
}
/** @constructor */
function $c_s_concurrent_Promise$() {
}
$p = $c_s_concurrent_Promise$.prototype = new $h_O();
$p.constructor = $c_s_concurrent_Promise$;
/** @constructor */
function $h_s_concurrent_Promise$() {
}
$h_s_concurrent_Promise$.prototype = $p;
$p.qH = (function(exception) {
  return $ct_s_concurrent_impl_Promise$DefaultPromise__s_util_Try__(new $c_s_concurrent_impl_Promise$DefaultPromise(), new $c_s_util_Failure(exception));
});
var $d_s_concurrent_Promise$ = new $TypeData().i($c_s_concurrent_Promise$, "scala.concurrent.Promise$", ({
  hv: 1
}));
var $n_s_concurrent_Promise$;
function $m_s_concurrent_Promise$() {
  if ((!$n_s_concurrent_Promise$)) {
    $n_s_concurrent_Promise$ = new $c_s_concurrent_Promise$();
  }
  return $n_s_concurrent_Promise$;
}
function $p_s_concurrent_duration_Duration$__words__T__sci_List($thiz, s) {
  var this$2 = $m_s_Predef$().sq($f_T__split__T__I__AT($f_T__trim__T(s), "\\s+", 0));
  $m_sci_List$();
  return $m_sci_Nil$().cc(this$2);
}
function $p_s_concurrent_duration_Duration$__expandLabels__T__sci_List($thiz, labels) {
  matchResult3: {
    var \u03b41$___1;
    var \u03b41$___2;
    var x5 = $p_s_concurrent_duration_Duration$__words__T__sci_List($thiz, labels);
    if ((x5 instanceof $c_sci_$colon$colon)) {
      var x8 = x5.K;
      var \u03b41$___1 = x5.gb;
      var \u03b41$___2 = x8;
      break matchResult3;
    }
    throw new $c_s_MatchError(x5);
  }
  var hd$2 = \u03b41$___1;
  var rest$2 = \u03b41$___2;
  var rest = rest$2;
  var h = null;
  var t = null;
  while ((rest !== $m_sci_Nil$())) {
    var x0 = rest.m();
    var it = new $c_sc_StrictOptimizedLinearSeqOps$$anon$1(new $c_sci_$colon$colon(x0, new $c_sci_$colon$colon((x0 + "s"), $m_sci_Nil$())));
    while (it.k()) {
      var nx = new $c_sci_$colon$colon(it.f(), $m_sci_Nil$());
      if ((t === null)) {
        h = nx;
      } else {
        t.K = nx;
      }
      t = nx;
    }
    rest = rest.o();
  }
  return new $c_sci_$colon$colon(hd$2, ((h === null) ? $m_sci_Nil$() : h));
}
/** @constructor */
function $c_s_concurrent_duration_Duration$() {
  this.np = null;
  this.pm = null;
  $n_s_concurrent_duration_Duration$ = this;
  var timeUnitLabels = new $c_sci_$colon$colon($ct_T2__O__O__(new $c_T2(), $m_s_concurrent_duration_package$().iA, "d day"), new $c_sci_$colon$colon($ct_T2__O__O__(new $c_T2(), $m_s_concurrent_duration_package$().k8, "h hr hour"), new $c_sci_$colon$colon($ct_T2__O__O__(new $c_T2(), $m_s_concurrent_duration_package$().ka, "m min minute"), new $c_sci_$colon$colon($ct_T2__O__O__(new $c_T2(), $m_s_concurrent_duration_package$().kc, "s sec second"), new $c_sci_$colon$colon($ct_T2__O__O__(new $c_T2(), $m_s_concurrent_duration_package$().iB, "ms milli millisecond"), new $c_sci_$colon$colon($ct_T2__O__O__(new $c_T2(), $m_s_concurrent_duration_package$().k9, "\u00b5s micro microsecond"), new $c_sci_$colon$colon($ct_T2__O__O__(new $c_T2(), $m_s_concurrent_duration_package$().kb, "ns nano nanosecond"), $m_sci_Nil$())))))));
  this.np = $m_sci_Map$().hI(new $c_sc_MapView$MapValues(new $c_sc_MapView$Id($m_sci_Map$().hI(timeUnitLabels)), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((s$2) => $p_s_concurrent_duration_Duration$__words__T__sci_List(this, s$2).oy()))));
  var $x_2 = $m_sci_Map$();
  var rest = timeUnitLabels;
  var h = null;
  var t = null;
  while ((rest !== $m_sci_Nil$())) {
    var x0 = rest.m();
    var unit = x0.aw();
    var names = x0.ap();
    var this$30 = $p_s_concurrent_duration_Duration$__expandLabels__T__sci_List(this, names);
    var f$1 = ((unit) => ((_$4$2) => $ct_T2__O__O__(new $c_T2(), _$4$2, unit)))(unit);
    if ((this$30 === $m_sci_Nil$())) {
      var $x_1 = $m_sci_Nil$();
    } else {
      var x0$1 = this$30.m();
      var h$1 = new $c_sci_$colon$colon(f$1(x0$1), $m_sci_Nil$());
      var t$1 = h$1;
      var rest$1 = this$30.o();
      while ((rest$1 !== $m_sci_Nil$())) {
        var x0$2 = rest$1.m();
        var nx = new $c_sci_$colon$colon(f$1(x0$2), $m_sci_Nil$());
        t$1.K = nx;
        t$1 = nx;
        rest$1 = rest$1.o();
      }
      var $x_1 = h$1;
    }
    var it = $x_1.c();
    while (it.k()) {
      var nx$1 = new $c_sci_$colon$colon(it.f(), $m_sci_Nil$());
      if ((t === null)) {
        h = nx$1;
      } else {
        t.K = nx$1;
      }
      t = nx$1;
    }
    rest = rest.o();
  }
  $x_2.hI(((h === null) ? $m_sci_Nil$() : h));
  new $c_s_concurrent_duration_FiniteDuration(0, 0, $m_s_concurrent_duration_package$().iA);
  this.pm = new $c_s_concurrent_duration_Duration$$anon$1();
}
$p = $c_s_concurrent_duration_Duration$.prototype = new $h_O();
$p.constructor = $c_s_concurrent_duration_Duration$;
/** @constructor */
function $h_s_concurrent_duration_Duration$() {
}
$h_s_concurrent_duration_Duration$.prototype = $p;
var $d_s_concurrent_duration_Duration$ = new $TypeData().i($c_s_concurrent_duration_Duration$, "scala.concurrent.duration.Duration$", ({
  hw: 1
}));
var $n_s_concurrent_duration_Duration$;
function $m_s_concurrent_duration_Duration$() {
  if ((!$n_s_concurrent_duration_Duration$)) {
    $n_s_concurrent_duration_Duration$ = new $c_s_concurrent_duration_Duration$();
  }
  return $n_s_concurrent_duration_Duration$;
}
/** @constructor */
function $c_s_concurrent_duration_package$() {
  this.iA = null;
  this.k8 = null;
  this.k9 = null;
  this.iB = null;
  this.ka = null;
  this.kb = null;
  this.kc = null;
  $n_s_concurrent_duration_package$ = this;
  this.iA = $m_ju_concurrent_TimeUnit$().m3;
  this.k8 = $m_ju_concurrent_TimeUnit$().m4;
  this.k9 = $m_ju_concurrent_TimeUnit$().m5;
  this.iB = $m_ju_concurrent_TimeUnit$().m6;
  this.ka = $m_ju_concurrent_TimeUnit$().m7;
  this.kb = $m_ju_concurrent_TimeUnit$().m8;
  this.kc = $m_ju_concurrent_TimeUnit$().m9;
}
$p = $c_s_concurrent_duration_package$.prototype = new $h_O();
$p.constructor = $c_s_concurrent_duration_package$;
/** @constructor */
function $h_s_concurrent_duration_package$() {
}
$h_s_concurrent_duration_package$.prototype = $p;
var $d_s_concurrent_duration_package$ = new $TypeData().i($c_s_concurrent_duration_package$, "scala.concurrent.duration.package$", ({
  hA: 1
}));
var $n_s_concurrent_duration_package$;
function $m_s_concurrent_duration_package$() {
  if ((!$n_s_concurrent_duration_package$)) {
    $n_s_concurrent_duration_package$ = new $c_s_concurrent_duration_package$();
  }
  return $n_s_concurrent_duration_package$;
}
/** @constructor */
function $c_s_concurrent_duration_package$DurationInt$() {
}
$p = $c_s_concurrent_duration_package$DurationInt$.prototype = new $h_O();
$p.constructor = $c_s_concurrent_duration_package$DurationInt$;
/** @constructor */
function $h_s_concurrent_duration_package$DurationInt$() {
}
$h_s_concurrent_duration_package$DurationInt$.prototype = $p;
$p.qz = (function(this$, x$0) {
  return ((x$0 instanceof $c_s_concurrent_duration_package$DurationInt) && (this$ === ((x$0 === null) ? (null | 0) : x$0.hj)));
});
var $d_s_concurrent_duration_package$DurationInt$ = new $TypeData().i($c_s_concurrent_duration_package$DurationInt$, "scala.concurrent.duration.package$DurationInt$", ({
  hB: 1
}));
var $n_s_concurrent_duration_package$DurationInt$;
function $m_s_concurrent_duration_package$DurationInt$() {
  if ((!$n_s_concurrent_duration_package$DurationInt$)) {
    $n_s_concurrent_duration_package$DurationInt$ = new $c_s_concurrent_duration_package$DurationInt$();
  }
  return $n_s_concurrent_duration_package$DurationInt$;
}
/** @constructor */
function $c_s_concurrent_impl_Promise$() {
  this.hk = null;
  $n_s_concurrent_impl_Promise$ = this;
  this.hk = $ct_s_concurrent_impl_Promise$Transformation__I__F1__s_concurrent_ExecutionContext__(new $c_s_concurrent_impl_Promise$Transformation(), 0, null, $m_s_concurrent_ExecutionContext$parasitic$());
}
$p = $c_s_concurrent_impl_Promise$.prototype = new $h_O();
$p.constructor = $c_s_concurrent_impl_Promise$;
/** @constructor */
function $h_s_concurrent_impl_Promise$() {
}
$h_s_concurrent_impl_Promise$.prototype = $p;
$p.hS = (function(value) {
  if ((value instanceof $c_s_util_Success)) {
    return value;
  } else {
    var t = value.f0;
    return (((false || false) || (t instanceof $c_jl_Error)) ? (false ? new $c_s_util_Success(t.p7()) : new $c_s_util_Failure(new $c_ju_concurrent_ExecutionException("Boxed Exception", t))) : value);
  }
});
var $d_s_concurrent_impl_Promise$ = new $TypeData().i($c_s_concurrent_impl_Promise$, "scala.concurrent.impl.Promise$", ({
  hC: 1
}));
var $n_s_concurrent_impl_Promise$;
function $m_s_concurrent_impl_Promise$() {
  if ((!$n_s_concurrent_impl_Promise$)) {
    $n_s_concurrent_impl_Promise$ = new $c_s_concurrent_impl_Promise$();
  }
  return $n_s_concurrent_impl_Promise$;
}
function $is_s_concurrent_impl_Promise$Callbacks(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.aJ)));
}
function $isArrayOf_s_concurrent_impl_Promise$Callbacks(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.aJ)));
}
/** @constructor */
function $c_s_reflect_ClassTag$() {
  this.pq = null;
  this.pz = null;
  this.pr = null;
  this.pu = null;
  this.pv = null;
  this.pt = null;
  this.ps = null;
  this.pp = null;
  this.pA = null;
  this.pn = null;
  this.py = null;
  this.po = null;
  this.pw = null;
  this.px = null;
  $n_s_reflect_ClassTag$ = this;
  this.pq = $m_s_reflect_ManifestFactory$ByteManifest$();
  this.pz = $m_s_reflect_ManifestFactory$ShortManifest$();
  this.pr = $m_s_reflect_ManifestFactory$CharManifest$();
  this.pu = $m_s_reflect_ManifestFactory$IntManifest$();
  this.pv = $m_s_reflect_ManifestFactory$LongManifest$();
  this.pt = $m_s_reflect_ManifestFactory$FloatManifest$();
  this.ps = $m_s_reflect_ManifestFactory$DoubleManifest$();
  this.pp = $m_s_reflect_ManifestFactory$BooleanManifest$();
  this.pA = $m_s_reflect_ManifestFactory$UnitManifest$();
  this.pn = $m_s_reflect_ManifestFactory$AnyManifest$();
  this.py = $m_s_reflect_ManifestFactory$ObjectManifest$();
  this.po = $m_s_reflect_ManifestFactory$ObjectManifest$();
  this.pw = $m_s_reflect_ManifestFactory$NothingManifest$();
  this.px = $m_s_reflect_ManifestFactory$NullManifest$();
}
$p = $c_s_reflect_ClassTag$.prototype = new $h_O();
$p.constructor = $c_s_reflect_ClassTag$;
/** @constructor */
function $h_s_reflect_ClassTag$() {
}
$h_s_reflect_ClassTag$.prototype = $p;
$p.kn = (function(runtimeClass1) {
  return ((runtimeClass1 === $d_B.l()) ? $m_s_reflect_ManifestFactory$ByteManifest$() : ((runtimeClass1 === $d_S.l()) ? $m_s_reflect_ManifestFactory$ShortManifest$() : ((runtimeClass1 === $d_C.l()) ? $m_s_reflect_ManifestFactory$CharManifest$() : ((runtimeClass1 === $d_I.l()) ? $m_s_reflect_ManifestFactory$IntManifest$() : ((runtimeClass1 === $d_J.l()) ? $m_s_reflect_ManifestFactory$LongManifest$() : ((runtimeClass1 === $d_F.l()) ? $m_s_reflect_ManifestFactory$FloatManifest$() : ((runtimeClass1 === $d_D.l()) ? $m_s_reflect_ManifestFactory$DoubleManifest$() : ((runtimeClass1 === $d_Z.l()) ? $m_s_reflect_ManifestFactory$BooleanManifest$() : ((runtimeClass1 === $d_V.l()) ? $m_s_reflect_ManifestFactory$UnitManifest$() : ((runtimeClass1 === $d_O.l()) ? $m_s_reflect_ManifestFactory$ObjectManifest$() : ((runtimeClass1 === $d_sr_Nothing$.l()) ? $m_s_reflect_ManifestFactory$NothingManifest$() : ((runtimeClass1 === $d_sr_Null$.l()) ? $m_s_reflect_ManifestFactory$NullManifest$() : new $c_s_reflect_ClassTag$GenericClassTag(runtimeClass1)))))))))))));
});
var $d_s_reflect_ClassTag$ = new $TypeData().i($c_s_reflect_ClassTag$, "scala.reflect.ClassTag$", ({
  hY: 1
}));
var $n_s_reflect_ClassTag$;
function $m_s_reflect_ClassTag$() {
  if ((!$n_s_reflect_ClassTag$)) {
    $n_s_reflect_ClassTag$ = new $c_s_reflect_ClassTag$();
  }
  return $n_s_reflect_ClassTag$;
}
/** @constructor */
function $c_sr_Arrays$() {
}
$p = $c_sr_Arrays$.prototype = new $h_O();
$p.constructor = $c_sr_Arrays$;
/** @constructor */
function $h_sr_Arrays$() {
}
$h_sr_Arrays$.prototype = $p;
$p.s0 = (function(xs, clazz) {
  var length = xs.l();
  var arr = clazz.J.U(length);
  xs.bc(arr, 0, 2147483647);
  return arr;
});
var $d_sr_Arrays$ = new $TypeData().i($c_sr_Arrays$, "scala.runtime.Arrays$", ({
  iu: 1
}));
var $n_sr_Arrays$;
function $m_sr_Arrays$() {
  if ((!$n_sr_Arrays$)) {
    $n_sr_Arrays$ = new $c_sr_Arrays$();
  }
  return $n_sr_Arrays$;
}
/** @constructor */
function $c_sr_BoxesRunTime$() {
}
$p = $c_sr_BoxesRunTime$.prototype = new $h_O();
$p.constructor = $c_sr_BoxesRunTime$;
/** @constructor */
function $h_sr_BoxesRunTime$() {
}
$h_sr_BoxesRunTime$.prototype = $p;
$p.n = (function(x, y) {
  return ((x === y) || ($is_jl_Number(x) ? this.qD(x, y) : ((x instanceof $Char) ? this.qB(x, y) : ((x === null) ? (y === null) : $dp_equals__O__Z(x, y)))));
});
$p.qD = (function(xn, y) {
  if ($is_jl_Number(y)) {
    return this.qC(xn, y);
  } else if ((y instanceof $Char)) {
    if (((typeof xn) === "number")) {
      return ((+xn) === y.c);
    } else if ((xn instanceof $Long)) {
      var $x_1 = $uJ(xn);
      var x3_$_lo = $x_1.l;
      var x3_$_hi = $x_1.h;
      var value = y.c;
      var hi = (value >> 31);
      return (((x3_$_lo ^ value) | (x3_$_hi ^ hi)) === 0);
    } else {
      return ((xn === null) ? (y === null) : $dp_equals__O__Z(xn, y));
    }
  } else {
    return ((xn === null) ? (y === null) : $dp_equals__O__Z(xn, y));
  }
});
$p.qC = (function(xn, yn) {
  if (((typeof xn) === "number")) {
    var x2 = (+xn);
    if (((typeof yn) === "number")) {
      return (x2 === (+yn));
    } else if ((yn instanceof $Long)) {
      var $x_1 = $uJ(yn);
      var x3_$_lo = $x_1.l;
      var x3_$_hi = $x_1.h;
      return (x2 === ((4.294967296E9 * x3_$_hi) + (x3_$_lo >>> 0.0)));
    } else {
      return (false && yn.p(x2));
    }
  } else if ((xn instanceof $Long)) {
    var $x_2 = $uJ(xn);
    var x3$2_$_lo = $x_2.l;
    var x3$2_$_hi = $x_2.h;
    if ((yn instanceof $Long)) {
      var $x_3 = $uJ(yn);
      var x2$3_$_lo = $x_3.l;
      var x2$3_$_hi = $x_3.h;
      return (((x3$2_$_lo ^ x2$3_$_lo) | (x3$2_$_hi ^ x2$3_$_hi)) === 0);
    } else if (((typeof yn) === "number")) {
      var x3$3 = (+yn);
      return (((4.294967296E9 * x3$2_$_hi) + (x3$2_$_lo >>> 0.0)) === x3$3);
    } else {
      return (false && yn.p($bL(x3$2_$_lo, x3$2_$_hi)));
    }
  } else {
    return ((xn === null) ? (yn === null) : $dp_equals__O__Z(xn, yn));
  }
});
$p.qB = (function(xc, y) {
  if ((y instanceof $Char)) {
    return (xc.c === y.c);
  } else if ($is_jl_Number(y)) {
    if (((typeof y) === "number")) {
      return ((+y) === xc.c);
    } else if ((y instanceof $Long)) {
      var $x_1 = $uJ(y);
      var x3_$_lo = $x_1.l;
      var x3_$_hi = $x_1.h;
      var value = xc.c;
      var hi = (value >> 31);
      return (((x3_$_lo ^ value) | (x3_$_hi ^ hi)) === 0);
    } else {
      return ((y === null) ? (xc === null) : $dp_equals__O__Z(y, xc));
    }
  } else {
    return ((xc === null) && (y === null));
  }
});
var $d_sr_BoxesRunTime$ = new $TypeData().i($c_sr_BoxesRunTime$, "scala.runtime.BoxesRunTime$", ({
  iv: 1
}));
var $n_sr_BoxesRunTime$;
function $m_sr_BoxesRunTime$() {
  if ((!$n_sr_BoxesRunTime$)) {
    $n_sr_BoxesRunTime$ = new $c_sr_BoxesRunTime$();
  }
  return $n_sr_BoxesRunTime$;
}
var $d_sr_Null$ = new $TypeData().i(0, "scala.runtime.Null$", ({
  iB: 1
}));
/** @constructor */
function $c_sr_RichLong$() {
}
$p = $c_sr_RichLong$.prototype = new $h_O();
$p.constructor = $c_sr_RichLong$;
/** @constructor */
function $h_sr_RichLong$() {
}
$h_sr_RichLong$.prototype = $p;
$p.qA = (function(this$_$_lo, this$_$_hi, x$0) {
  if ((x$0 instanceof $c_sr_RichLong)) {
    if ((x$0 === null)) {
      var $x_1 = $uJ(null);
      var x$0$2_$_lo = $x_1.l;
      var x$0$2_$_hi = $x_1.h;
    } else {
      var $x_2 = x$0;
      var x$0$2_$_lo = $x_2.fy;
      var x$0$2_$_hi = $x_2.fz;
    }
    return (((this$_$_lo ^ x$0$2_$_lo) | (this$_$_hi ^ x$0$2_$_hi)) === 0);
  } else {
    return false;
  }
});
var $d_sr_RichLong$ = new $TypeData().i($c_sr_RichLong$, "scala.runtime.RichLong$", ({
  iF: 1
}));
var $n_sr_RichLong$;
function $m_sr_RichLong$() {
  if ((!$n_sr_RichLong$)) {
    $n_sr_RichLong$ = new $c_sr_RichLong$();
  }
  return $n_sr_RichLong$;
}
/** @constructor */
function $c_sr_Scala3RunTime$() {
}
$p = $c_sr_Scala3RunTime$.prototype = new $h_O();
$p.constructor = $c_sr_Scala3RunTime$;
/** @constructor */
function $h_sr_Scala3RunTime$() {
}
$h_sr_Scala3RunTime$.prototype = $p;
$p.bV = (function() {
  throw $ct_jl_NullPointerException__T__(new $c_jl_NullPointerException(), "tried to cast away nullability, but value is null");
});
var $d_sr_Scala3RunTime$ = new $TypeData().i($c_sr_Scala3RunTime$, "scala.runtime.Scala3RunTime$", ({
  iG: 1
}));
var $n_sr_Scala3RunTime$;
function $m_sr_Scala3RunTime$() {
  if ((!$n_sr_Scala3RunTime$)) {
    $n_sr_Scala3RunTime$ = new $c_sr_Scala3RunTime$();
  }
  return $n_sr_Scala3RunTime$;
}
/** @constructor */
function $c_sr_ScalaRunTime$() {
}
$p = $c_sr_ScalaRunTime$.prototype = new $h_O();
$p.constructor = $c_sr_ScalaRunTime$;
/** @constructor */
function $h_sr_ScalaRunTime$() {
}
$h_sr_ScalaRunTime$.prototype = $p;
$p.aM = (function(xs, idx) {
  if ((xs instanceof $ac_O)) {
    return xs.a[idx];
  }
  if ((xs instanceof $ac_I)) {
    return xs.a[idx];
  }
  if ((xs instanceof $ac_D)) {
    return xs.a[idx];
  }
  if ((xs instanceof $ac_J)) {
    var $x_1 = xs.a;
    var $x_2 = (idx << 1);
    return $bL($x_1[$x_2], $x_1[(($x_2 + 1) | 0)]);
  }
  if ((xs instanceof $ac_F)) {
    return xs.a[idx];
  }
  if ((xs instanceof $ac_C)) {
    return $bC(xs.a[idx]);
  }
  if ((xs instanceof $ac_B)) {
    return xs.a[idx];
  }
  if ((xs instanceof $ac_S)) {
    return xs.a[idx];
  }
  if ((xs instanceof $ac_Z)) {
    return xs.a[idx];
  }
  if ((xs === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  throw new $c_s_MatchError(xs);
});
$p.d3 = (function(xs, idx, value) {
  if ((xs instanceof $ac_O)) {
    xs.a[idx] = value;
    return (void 0);
  }
  if ((xs instanceof $ac_I)) {
    xs.a[idx] = (value | 0);
    return (void 0);
  }
  if ((xs instanceof $ac_D)) {
    xs.a[idx] = (+value);
    return (void 0);
  }
  if ((xs instanceof $ac_J)) {
    var $x_1 = $uJ(value);
    var $x_2 = xs.a;
    var $x_3 = (idx << 1);
    $x_2[$x_3] = $x_1.l;
    $x_2[(($x_3 + 1) | 0)] = $x_1.h;
    return (void 0);
  }
  if ((xs instanceof $ac_F)) {
    xs.a[idx] = Math.fround(value);
    return (void 0);
  }
  if ((xs instanceof $ac_C)) {
    xs.a[idx] = $uC(value);
    return (void 0);
  }
  if ((xs instanceof $ac_B)) {
    xs.a[idx] = (value | 0);
    return (void 0);
  }
  if ((xs instanceof $ac_S)) {
    xs.a[idx] = (value | 0);
    return (void 0);
  }
  if ((xs instanceof $ac_Z)) {
    xs.a[idx] = (!(!value));
    return (void 0);
  }
  if ((xs === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  throw new $c_s_MatchError(xs);
});
$p.nU = (function(xs) {
  if ((xs instanceof $ac_O)) {
    return xs.h();
  }
  if ((xs instanceof $ac_I)) {
    return xs.h();
  }
  if ((xs instanceof $ac_D)) {
    return xs.h();
  }
  if ((xs instanceof $ac_J)) {
    return xs.h();
  }
  if ((xs instanceof $ac_F)) {
    return xs.h();
  }
  if ((xs instanceof $ac_C)) {
    return xs.h();
  }
  if ((xs instanceof $ac_B)) {
    return xs.h();
  }
  if ((xs instanceof $ac_S)) {
    return xs.h();
  }
  if ((xs instanceof $ac_Z)) {
    return xs.h();
  }
  if ((xs === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  throw new $c_s_MatchError(xs);
});
$p.dL = (function(x) {
  return $f_sc_IterableOnceOps__mkString__T__T__T__T(x.cd(), (x.bG() + "("), ",", ")");
});
$p.r1 = (function(xs) {
  return ((xs === null) ? null : $m_sci_ArraySeq$().gB(xs));
});
var $d_sr_ScalaRunTime$ = new $TypeData().i($c_sr_ScalaRunTime$, "scala.runtime.ScalaRunTime$", ({
  iI: 1
}));
var $n_sr_ScalaRunTime$;
function $m_sr_ScalaRunTime$() {
  if ((!$n_sr_ScalaRunTime$)) {
    $n_sr_ScalaRunTime$ = new $c_sr_ScalaRunTime$();
  }
  return $n_sr_ScalaRunTime$;
}
/** @constructor */
function $c_sr_Statics$() {
}
$p = $c_sr_Statics$.prototype = new $h_O();
$p.constructor = $c_sr_Statics$;
/** @constructor */
function $h_sr_Statics$() {
}
$h_sr_Statics$.prototype = $p;
$p.x = (function(hash, data) {
  var h = this.eq(hash, data);
  var i = h;
  h = ((i << 13) | ((i >>> 19) | 0));
  return ((Math.imul(5, h) - 430675100) | 0);
});
$p.eq = (function(hash, data) {
  var k = data;
  k = Math.imul((-862048943), k);
  var i = k;
  k = ((i << 15) | ((i >>> 17) | 0));
  k = Math.imul(461845907, k);
  return (hash ^ k);
});
$p.bd = (function(hash, length) {
  return this.q6((hash ^ length));
});
$p.q6 = (function(h0) {
  var h = h0;
  h = (h ^ ((h >>> 16) | 0));
  h = Math.imul((-2048144789), h);
  h = (h ^ ((h >>> 13) | 0));
  h = Math.imul((-1028477387), h);
  h = (h ^ ((h >>> 16) | 0));
  return h;
});
$p.rp = (function(lv_$_lo, lv_$_hi) {
  return ((lv_$_hi === (lv_$_lo >> 31)) ? lv_$_lo : (lv_$_lo ^ lv_$_hi));
});
$p.qu = (function(dv) {
  var iv = $doubleToInt(dv);
  if ((iv === dv)) {
    return iv;
  } else {
    var $x_1 = $m_RTLong$().kL(dv);
    var lv_$_lo = $x_1.l;
    var lv_$_hi = $x_1.h;
    if ((((4.294967296E9 * lv_$_hi) + (lv_$_lo >>> 0.0)) === dv)) {
      return (lv_$_lo ^ lv_$_hi);
    } else {
      var valueInt = (dv | 0);
      if (((valueInt === dv) && ((1.0 / dv) !== (-Infinity)))) {
        return valueInt;
      } else if ((dv !== dv)) {
        return 2146959360;
      } else {
        var fpBitsDataView = $fpBitsDataView;
        fpBitsDataView.setFloat64(0, dv, true);
        return ((fpBitsDataView.getInt32(0, true) | 0) ^ (fpBitsDataView.getInt32(4, true) | 0));
      }
    }
  }
});
$p.y = (function(x) {
  if ((x === null)) {
    return 0;
  } else if (((typeof x) === "number")) {
    return this.qu((+x));
  } else if ((x instanceof $Long)) {
    var $x_1 = $uJ(x);
    return this.rp($x_1.l, $x_1.h);
  } else {
    return $dp_hashCode__I(x);
  }
});
$p.re = (function(n) {
  throw $ct_jl_IndexOutOfBoundsException__T__(new $c_jl_IndexOutOfBoundsException(), ("" + n));
});
var $d_sr_Statics$ = new $TypeData().i($c_sr_Statics$, "scala.runtime.Statics$", ({
  iL: 1
}));
var $n_sr_Statics$;
function $m_sr_Statics$() {
  if ((!$n_sr_Statics$)) {
    $n_sr_Statics$ = new $c_sr_Statics$();
  }
  return $n_sr_Statics$;
}
/** @constructor */
function $c_sr_Statics$PFMarker$() {
}
$p = $c_sr_Statics$PFMarker$.prototype = new $h_O();
$p.constructor = $c_sr_Statics$PFMarker$;
/** @constructor */
function $h_sr_Statics$PFMarker$() {
}
$h_sr_Statics$PFMarker$.prototype = $p;
var $d_sr_Statics$PFMarker$ = new $TypeData().i($c_sr_Statics$PFMarker$, "scala.runtime.Statics$PFMarker$", ({
  iM: 1
}));
var $n_sr_Statics$PFMarker$;
function $m_sr_Statics$PFMarker$() {
  if ((!$n_sr_Statics$PFMarker$)) {
    $n_sr_Statics$PFMarker$ = new $c_sr_Statics$PFMarker$();
  }
  return $n_sr_Statics$PFMarker$;
}
/** @constructor */
function $c_sjs_concurrent_JSExecutionContext$() {
  this.ny = null;
  $n_sjs_concurrent_JSExecutionContext$ = this;
  this.ny = $m_sjs_concurrent_QueueExecutionContext$().q4();
}
$p = $c_sjs_concurrent_JSExecutionContext$.prototype = new $h_O();
$p.constructor = $c_sjs_concurrent_JSExecutionContext$;
/** @constructor */
function $h_sjs_concurrent_JSExecutionContext$() {
}
$h_sjs_concurrent_JSExecutionContext$.prototype = $p;
var $d_sjs_concurrent_JSExecutionContext$ = new $TypeData().i($c_sjs_concurrent_JSExecutionContext$, "scala.scalajs.concurrent.JSExecutionContext$", ({
  iN: 1
}));
var $n_sjs_concurrent_JSExecutionContext$;
function $m_sjs_concurrent_JSExecutionContext$() {
  if ((!$n_sjs_concurrent_JSExecutionContext$)) {
    $n_sjs_concurrent_JSExecutionContext$ = new $c_sjs_concurrent_JSExecutionContext$();
  }
  return $n_sjs_concurrent_JSExecutionContext$;
}
/** @constructor */
function $c_sjs_concurrent_QueueExecutionContext$() {
}
$p = $c_sjs_concurrent_QueueExecutionContext$.prototype = new $h_O();
$p.constructor = $c_sjs_concurrent_QueueExecutionContext$;
/** @constructor */
function $h_sjs_concurrent_QueueExecutionContext$() {
}
$h_sjs_concurrent_QueueExecutionContext$.prototype = $p;
$p.q4 = (function() {
  return (((typeof Promise) === "undefined") ? new $c_sjs_concurrent_QueueExecutionContext$TimeoutsExecutionContext() : new $c_sjs_concurrent_QueueExecutionContext$PromisesExecutionContext());
});
var $d_sjs_concurrent_QueueExecutionContext$ = new $TypeData().i($c_sjs_concurrent_QueueExecutionContext$, "scala.scalajs.concurrent.QueueExecutionContext$", ({
  iO: 1
}));
var $n_sjs_concurrent_QueueExecutionContext$;
function $m_sjs_concurrent_QueueExecutionContext$() {
  if ((!$n_sjs_concurrent_QueueExecutionContext$)) {
    $n_sjs_concurrent_QueueExecutionContext$ = new $c_sjs_concurrent_QueueExecutionContext$();
  }
  return $n_sjs_concurrent_QueueExecutionContext$;
}
/** @constructor */
function $c_sjs_js_ArrayOps$() {
}
$p = $c_sjs_js_ArrayOps$.prototype = new $h_O();
$p.constructor = $c_sjs_js_ArrayOps$;
/** @constructor */
function $h_sjs_js_ArrayOps$() {
}
$h_sjs_js_ArrayOps$.prototype = $p;
$p.rR = (function(this$) {
  var len = (this$.length | 0);
  var res = new Array(len);
  var i = 0;
  while ((i < len)) {
    res[((((len - i) | 0) - 1) | 0)] = this$[i];
    i = ((1 + i) | 0);
  }
  return res;
});
$p.se = (function(this$, ord) {
  return $f_sc_SeqOps__sorted__s_math_Ordering__O($ct_sjs_js_WrappedArray__sjs_js_Array__(new $c_sjs_js_WrappedArray(), this$), ord).ek;
});
$p.sb = (function(this$, f, ord) {
  return $m_sjs_js_ArrayOps$().se(this$, new $c_s_math_Ordering$$anon$1(f, ord));
});
$p.pY = (function(this$, x) {
  var dest = this$.slice();
  dest.push(x);
  return dest;
});
$p.qP = (function(this$, f) {
  var len = (this$.length | 0);
  var i = 0;
  while ((i < len)) {
    f.d(this$[i]);
    i = ((1 + i) | 0);
  }
});
$p.qr = (function(this$, f) {
  var result = [];
  $m_sjs_js_ArrayOps$().pI(result, new $c_sc_Iterator$$anon$8(f, new $c_sjs_js_ArrayOps$ArrayIterator(this$)));
  return result;
});
$p.pI = (function(this$, ys) {
  var this$1 = ys.c();
  while (this$1.k()) {
    var x0 = this$1.f();
    this$.push(x0);
  }
  return this$;
});
var $d_sjs_js_ArrayOps$ = new $TypeData().i($c_sjs_js_ArrayOps$, "scala.scalajs.js.ArrayOps$", ({
  iS: 1
}));
var $n_sjs_js_ArrayOps$;
function $m_sjs_js_ArrayOps$() {
  if ((!$n_sjs_js_ArrayOps$)) {
    $n_sjs_js_ArrayOps$ = new $c_sjs_js_ArrayOps$();
  }
  return $n_sjs_js_ArrayOps$;
}
/** @constructor */
function $c_sjs_js_Thenable$Implicits$() {
}
$p = $c_sjs_js_Thenable$Implicits$.prototype = new $h_O();
$p.constructor = $c_sjs_js_Thenable$Implicits$;
/** @constructor */
function $h_sjs_js_Thenable$Implicits$() {
}
$h_sjs_js_Thenable$Implicits$.prototype = $p;
$p.p0 = (function(p) {
  return $m_sjs_js_Thenable$ThenableOps$().sh(p);
});
var $d_sjs_js_Thenable$Implicits$ = new $TypeData().i($c_sjs_js_Thenable$Implicits$, "scala.scalajs.js.Thenable$Implicits$", ({
  iW: 1
}));
var $n_sjs_js_Thenable$Implicits$;
function $m_sjs_js_Thenable$Implicits$() {
  if ((!$n_sjs_js_Thenable$Implicits$)) {
    $n_sjs_js_Thenable$Implicits$ = new $c_sjs_js_Thenable$Implicits$();
  }
  return $n_sjs_js_Thenable$Implicits$;
}
/** @constructor */
function $c_sjs_js_Thenable$ThenableOps$() {
}
$p = $c_sjs_js_Thenable$ThenableOps$.prototype = new $h_O();
$p.constructor = $c_sjs_js_Thenable$ThenableOps$;
/** @constructor */
function $h_sjs_js_Thenable$ThenableOps$() {
}
$h_sjs_js_Thenable$ThenableOps$.prototype = $p;
$p.sh = (function(this$) {
  var p2 = $ct_s_concurrent_impl_Promise$DefaultPromise__(new $c_s_concurrent_impl_Promise$DefaultPromise());
  this$.then(((arg1$2) => {
    $f_s_concurrent_Promise__success__O__s_concurrent_Promise(p2, arg1$2);
  }), $m_sjs_js_defined$().q2(((arg1$2$1) => {
    $f_s_concurrent_Promise__failure__jl_Throwable__s_concurrent_Promise(p2, ((arg1$2$1 instanceof $c_jl_Throwable) ? arg1$2$1 : new $c_sjs_js_JavaScriptException(arg1$2$1)));
  })));
  return p2;
});
var $d_sjs_js_Thenable$ThenableOps$ = new $TypeData().i($c_sjs_js_Thenable$ThenableOps$, "scala.scalajs.js.Thenable$ThenableOps$", ({
  iX: 1
}));
var $n_sjs_js_Thenable$ThenableOps$;
function $m_sjs_js_Thenable$ThenableOps$() {
  if ((!$n_sjs_js_Thenable$ThenableOps$)) {
    $n_sjs_js_Thenable$ThenableOps$ = new $c_sjs_js_Thenable$ThenableOps$();
  }
  return $n_sjs_js_Thenable$ThenableOps$;
}
/** @constructor */
function $c_sjs_js_WrappedDictionary$Cache$() {
  this.nA = null;
  $n_sjs_js_WrappedDictionary$Cache$ = this;
  this.nA = Object.prototype.hasOwnProperty;
}
$p = $c_sjs_js_WrappedDictionary$Cache$.prototype = new $h_O();
$p.constructor = $c_sjs_js_WrappedDictionary$Cache$;
/** @constructor */
function $h_sjs_js_WrappedDictionary$Cache$() {
}
$h_sjs_js_WrappedDictionary$Cache$.prototype = $p;
var $d_sjs_js_WrappedDictionary$Cache$ = new $TypeData().i($c_sjs_js_WrappedDictionary$Cache$, "scala.scalajs.js.WrappedDictionary$Cache$", ({
  j0: 1
}));
var $n_sjs_js_WrappedDictionary$Cache$;
function $m_sjs_js_WrappedDictionary$Cache$() {
  if ((!$n_sjs_js_WrappedDictionary$Cache$)) {
    $n_sjs_js_WrappedDictionary$Cache$ = new $c_sjs_js_WrappedDictionary$Cache$();
  }
  return $n_sjs_js_WrappedDictionary$Cache$;
}
/** @constructor */
function $c_sjs_js_defined$() {
}
$p = $c_sjs_js_defined$.prototype = new $h_O();
$p.constructor = $c_sjs_js_defined$;
/** @constructor */
function $h_sjs_js_defined$() {
}
$h_sjs_js_defined$.prototype = $p;
$p.q2 = (function(a) {
  return a;
});
var $d_sjs_js_defined$ = new $TypeData().i($c_sjs_js_defined$, "scala.scalajs.js.defined$", ({
  j2: 1
}));
var $n_sjs_js_defined$;
function $m_sjs_js_defined$() {
  if ((!$n_sjs_js_defined$)) {
    $n_sjs_js_defined$ = new $c_sjs_js_defined$();
  }
  return $n_sjs_js_defined$;
}
/** @constructor */
function $c_sjs_js_timers_package$() {
}
$p = $c_sjs_js_timers_package$.prototype = new $h_O();
$p.constructor = $c_sjs_js_timers_package$;
/** @constructor */
function $h_sjs_js_timers_package$() {
}
$h_sjs_js_timers_package$.prototype = $p;
$p.s3 = (function(interval, body) {
  var $x_4 = setTimeout;
  var $x_3 = interval.eX;
  var $x_2 = interval;
  var $x_1 = $x_3.fK($x_2.dj, $x_2.dk);
  var x_$_lo = $x_1.l;
  var x_$_hi = $x_1.h;
  return $x_4((() => {
    body.a2();
  }), ((4.294967296E9 * x_$_hi) + (x_$_lo >>> 0.0)));
});
$p.qb = (function(handle) {
  clearTimeout(handle);
});
var $d_sjs_js_timers_package$ = new $TypeData().i($c_sjs_js_timers_package$, "scala.scalajs.js.timers.package$", ({
  j3: 1
}));
var $n_sjs_js_timers_package$;
function $m_sjs_js_timers_package$() {
  if ((!$n_sjs_js_timers_package$)) {
    $n_sjs_js_timers_package$ = new $c_sjs_js_timers_package$();
  }
  return $n_sjs_js_timers_package$;
}
/** @constructor */
function $c_sjsr_package$() {
}
$p = $c_sjsr_package$.prototype = new $h_O();
$p.constructor = $c_sjsr_package$;
/** @constructor */
function $h_sjsr_package$() {
}
$h_sjsr_package$.prototype = $p;
$p.I = (function(array) {
  var len = $m_jl_reflect_Array$().b8(array);
  var result = [];
  var i = 0;
  while ((i !== len)) {
    result.push($m_sr_ScalaRunTime$().aM(array, i));
    i = ((1 + i) | 0);
  }
  return result;
});
$p.z = (function(array) {
  var len = array.a.length;
  var result = [];
  var i = 0;
  while ((i !== len)) {
    var x1 = i;
    result.push(array.a[x1]);
    i = ((1 + i) | 0);
  }
  return result;
});
var $d_sjsr_package$ = new $TypeData().i($c_sjsr_package$, "scala.scalajs.runtime.package$", ({
  j6: 1
}));
var $n_sjsr_package$;
function $m_sjsr_package$() {
  if ((!$n_sjsr_package$)) {
    $n_sjsr_package$ = new $c_sjsr_package$();
  }
  return $n_sjsr_package$;
}
/** @constructor */
function $c_s_util_ChainingOps$() {
}
$p = $c_s_util_ChainingOps$.prototype = new $h_O();
$p.constructor = $c_s_util_ChainingOps$;
/** @constructor */
function $h_s_util_ChainingOps$() {
}
$h_s_util_ChainingOps$.prototype = $p;
$p.f4 = (function(this$, f) {
  f.d(this$);
  return this$;
});
var $d_s_util_ChainingOps$ = new $TypeData().i($c_s_util_ChainingOps$, "scala.util.ChainingOps$", ({
  j7: 1
}));
var $n_s_util_ChainingOps$;
function $m_s_util_ChainingOps$() {
  if ((!$n_s_util_ChainingOps$)) {
    $n_s_util_ChainingOps$ = new $c_s_util_ChainingOps$();
  }
  return $n_s_util_ChainingOps$;
}
/** @constructor */
function $c_s_util_CommandLineParser$() {
}
$p = $c_s_util_CommandLineParser$.prototype = new $h_O();
$p.constructor = $c_s_util_CommandLineParser$;
/** @constructor */
function $h_s_util_CommandLineParser$() {
}
$h_s_util_CommandLineParser$.prototype = $p;
$p.s4 = (function(err) {
  var where = ((err.os() === 0) ? "" : ((err.os() === 1) ? " after first argument" : ((" after " + err.os()) + " arguments")));
  var x = ((("Illegal command line" + where) + ": ") + err.sx());
  $m_s_Console$().rE().kV((x + "\n"));
});
var $d_s_util_CommandLineParser$ = new $TypeData().i($c_s_util_CommandLineParser$, "scala.util.CommandLineParser$", ({
  j8: 1
}));
var $n_s_util_CommandLineParser$;
function $m_s_util_CommandLineParser$() {
  if ((!$n_s_util_CommandLineParser$)) {
    $n_s_util_CommandLineParser$ = new $c_s_util_CommandLineParser$();
  }
  return $n_s_util_CommandLineParser$;
}
/** @constructor */
function $c_s_util_DynamicVariable(init) {
  this.ki = null;
  this.ki = init;
}
$p = $c_s_util_DynamicVariable.prototype = new $h_O();
$p.constructor = $c_s_util_DynamicVariable;
/** @constructor */
function $h_s_util_DynamicVariable() {
}
$h_s_util_DynamicVariable.prototype = $p;
$p.t = (function() {
  return (("DynamicVariable(" + this.ki) + ")");
});
var $d_s_util_DynamicVariable = new $TypeData().i($c_s_util_DynamicVariable, "scala.util.DynamicVariable", ({
  ja: 1
}));
/** @constructor */
function $c_s_util_Sorting$() {
}
$p = $c_s_util_Sorting$.prototype = new $h_O();
$p.constructor = $c_s_util_Sorting$;
/** @constructor */
function $h_s_util_Sorting$() {
}
$h_s_util_Sorting$.prototype = $p;
$p.aV = (function(a, i0, iN, ord) {
  var n = ((iN - i0) | 0);
  if ((n < 2)) {
    return (void 0);
  }
  if ((ord.ax($m_sr_ScalaRunTime$().aM(a, i0), $m_sr_ScalaRunTime$().aM(a, ((1 + i0) | 0))) > 0)) {
    var temp = $m_sr_ScalaRunTime$().aM(a, i0);
    $m_sr_ScalaRunTime$().d3(a, i0, $m_sr_ScalaRunTime$().aM(a, ((1 + i0) | 0)));
    $m_sr_ScalaRunTime$().d3(a, ((1 + i0) | 0), temp);
  }
  var m = 2;
  while ((m < n)) {
    var next = $m_sr_ScalaRunTime$().aM(a, ((i0 + m) | 0));
    if ((ord.ax(next, $m_sr_ScalaRunTime$().aM(a, ((((i0 + m) | 0) - 1) | 0))) < 0)) {
      var iA = i0;
      var iB = ((((i0 + m) | 0) - 1) | 0);
      while ((((iB - iA) | 0) > 1)) {
        var ix = ((((iA + iB) | 0) >>> 1) | 0);
        if ((ord.ax(next, $m_sr_ScalaRunTime$().aM(a, ix)) < 0)) {
          iB = ix;
        } else {
          iA = ix;
        }
      }
      var ix$2 = ((iA + ((ord.ax(next, $m_sr_ScalaRunTime$().aM(a, iA)) >= 0) | 0)) | 0);
      var i = ((i0 + m) | 0);
      while ((i > ix$2)) {
        $m_sr_ScalaRunTime$().d3(a, i, $m_sr_ScalaRunTime$().aM(a, ((i - 1) | 0)));
        i = ((i - 1) | 0);
      }
      $m_sr_ScalaRunTime$().d3(a, ix$2, next);
    }
    m = ((1 + m) | 0);
  }
});
$p.U = (function(a, i0, iN, ord, scratch, evidence$2) {
  if ((((iN - i0) | 0) < 32)) {
    this.aV(a, i0, iN, ord);
  } else {
    var iK = ((((i0 + iN) | 0) >>> 1) | 0);
    var sc = ((scratch === null) ? evidence$2.bU(((iK - i0) | 0)) : scratch);
    this.U(a, i0, iK, ord, sc, evidence$2);
    this.U(a, iK, iN, ord, sc, evidence$2);
    this.aW(a, i0, iK, iN, ord, sc);
  }
});
$p.aW = (function(a, i0, iK, iN, ord, scratch) {
  if ((ord.ax($m_sr_ScalaRunTime$().aM(a, ((iK - 1) | 0)), $m_sr_ScalaRunTime$().aM(a, iK)) > 0)) {
    var i = i0;
    var jN = ((iK - i0) | 0);
    var j = 0;
    while ((i < iK)) {
      $m_sr_ScalaRunTime$().d3(scratch, j, $m_sr_ScalaRunTime$().aM(a, i));
      i = ((1 + i) | 0);
      j = ((1 + j) | 0);
    }
    var k = i0;
    j = 0;
    while (((i < iN) && (j < jN))) {
      if ((ord.ax($m_sr_ScalaRunTime$().aM(a, i), $m_sr_ScalaRunTime$().aM(scratch, j)) < 0)) {
        $m_sr_ScalaRunTime$().d3(a, k, $m_sr_ScalaRunTime$().aM(a, i));
        i = ((1 + i) | 0);
      } else {
        $m_sr_ScalaRunTime$().d3(a, k, $m_sr_ScalaRunTime$().aM(scratch, j));
        j = ((1 + j) | 0);
      }
      k = ((1 + k) | 0);
    }
    while ((j < jN)) {
      $m_sr_ScalaRunTime$().d3(a, k, $m_sr_ScalaRunTime$().aM(scratch, j));
      j = ((1 + j) | 0);
      k = ((1 + k) | 0);
    }
  }
});
$p.rY = (function(a, from, until) {
  var i = from;
  var n = 0;
  while ((i < until)) {
    if ((!a.a[i])) {
      n = ((1 + n) | 0);
    }
    i = ((1 + i) | 0);
  }
  i = 0;
  while ((i < n)) {
    a.a[((from + i) | 0)] = false;
    i = ((1 + i) | 0);
  }
  while ((((from + i) | 0) < until)) {
    a.a[((from + i) | 0)] = true;
    i = ((1 + i) | 0);
  }
});
$p.l8 = (function(a, from, until, evidence$4) {
  $m_s_math_Ordering$();
  if ((a instanceof $ac_O)) {
    if ((($m_jl_reflect_Array$().b8(a) > 1) && (evidence$4 === null))) {
      throw $ct_jl_NullPointerException__T__(new $c_jl_NullPointerException(), "Ordering");
    }
    $m_ju_Arrays$().sa(a, from, until, evidence$4);
  } else if ((a instanceof $ac_I)) {
    if ((evidence$4 === $m_s_math_Ordering$Int$())) {
      $m_ju_Arrays$().s7(a, from, until);
    } else {
      var evidence$2 = $m_s_reflect_ManifestFactory$IntManifest$();
      if ((((until - from) | 0) < 32)) {
        this.aV(a, from, until, evidence$4);
      } else {
        var iK = ((((from + until) | 0) >>> 1) | 0);
        var sc = new $ac_I(((iK - from) | 0));
        if ((((iK - from) | 0) < 32)) {
          this.aV(a, from, iK, evidence$4);
        } else {
          var iK$1 = ((((from + iK) | 0) >>> 1) | 0);
          this.U(a, from, iK$1, evidence$4, sc, evidence$2);
          this.U(a, iK$1, iK, evidence$4, sc, evidence$2);
          this.aW(a, from, iK$1, iK, evidence$4, sc);
        }
        if ((((until - iK) | 0) < 32)) {
          this.aV(a, iK, until, evidence$4);
        } else {
          var iK$2 = ((((iK + until) | 0) >>> 1) | 0);
          this.U(a, iK, iK$2, evidence$4, sc, evidence$2);
          this.U(a, iK$2, until, evidence$4, sc, evidence$2);
          this.aW(a, iK, iK$2, until, evidence$4, sc);
        }
        this.aW(a, from, iK, until, evidence$4, sc);
      }
    }
  } else if ((a instanceof $ac_D)) {
    var evidence$2$1 = $m_s_reflect_ManifestFactory$DoubleManifest$();
    if ((((until - from) | 0) < 32)) {
      this.aV(a, from, until, evidence$4);
    } else {
      var iK$3 = ((((from + until) | 0) >>> 1) | 0);
      var sc$1 = new $ac_D(((iK$3 - from) | 0));
      if ((((iK$3 - from) | 0) < 32)) {
        this.aV(a, from, iK$3, evidence$4);
      } else {
        var iK$4 = ((((from + iK$3) | 0) >>> 1) | 0);
        this.U(a, from, iK$4, evidence$4, sc$1, evidence$2$1);
        this.U(a, iK$4, iK$3, evidence$4, sc$1, evidence$2$1);
        this.aW(a, from, iK$4, iK$3, evidence$4, sc$1);
      }
      if ((((until - iK$3) | 0) < 32)) {
        this.aV(a, iK$3, until, evidence$4);
      } else {
        var iK$5 = ((((iK$3 + until) | 0) >>> 1) | 0);
        this.U(a, iK$3, iK$5, evidence$4, sc$1, evidence$2$1);
        this.U(a, iK$5, until, evidence$4, sc$1, evidence$2$1);
        this.aW(a, iK$3, iK$5, until, evidence$4, sc$1);
      }
      this.aW(a, from, iK$3, until, evidence$4, sc$1);
    }
  } else if ((a instanceof $ac_J)) {
    if ((evidence$4 === $m_s_math_Ordering$Long$())) {
      $m_ju_Arrays$().s8(a, from, until);
    } else {
      var evidence$2$2 = $m_s_reflect_ManifestFactory$LongManifest$();
      if ((((until - from) | 0) < 32)) {
        this.aV(a, from, until, evidence$4);
      } else {
        var iK$6 = ((((from + until) | 0) >>> 1) | 0);
        var sc$2 = new $ac_J(((iK$6 - from) | 0));
        if ((((iK$6 - from) | 0) < 32)) {
          this.aV(a, from, iK$6, evidence$4);
        } else {
          var iK$7 = ((((from + iK$6) | 0) >>> 1) | 0);
          this.U(a, from, iK$7, evidence$4, sc$2, evidence$2$2);
          this.U(a, iK$7, iK$6, evidence$4, sc$2, evidence$2$2);
          this.aW(a, from, iK$7, iK$6, evidence$4, sc$2);
        }
        if ((((until - iK$6) | 0) < 32)) {
          this.aV(a, iK$6, until, evidence$4);
        } else {
          var iK$8 = ((((iK$6 + until) | 0) >>> 1) | 0);
          this.U(a, iK$6, iK$8, evidence$4, sc$2, evidence$2$2);
          this.U(a, iK$8, until, evidence$4, sc$2, evidence$2$2);
          this.aW(a, iK$6, iK$8, until, evidence$4, sc$2);
        }
        this.aW(a, from, iK$6, until, evidence$4, sc$2);
      }
    }
  } else if ((a instanceof $ac_F)) {
    var evidence$2$3 = $m_s_reflect_ManifestFactory$FloatManifest$();
    if ((((until - from) | 0) < 32)) {
      this.aV(a, from, until, evidence$4);
    } else {
      var iK$9 = ((((from + until) | 0) >>> 1) | 0);
      var sc$3 = new $ac_F(((iK$9 - from) | 0));
      if ((((iK$9 - from) | 0) < 32)) {
        this.aV(a, from, iK$9, evidence$4);
      } else {
        var iK$10 = ((((from + iK$9) | 0) >>> 1) | 0);
        this.U(a, from, iK$10, evidence$4, sc$3, evidence$2$3);
        this.U(a, iK$10, iK$9, evidence$4, sc$3, evidence$2$3);
        this.aW(a, from, iK$10, iK$9, evidence$4, sc$3);
      }
      if ((((until - iK$9) | 0) < 32)) {
        this.aV(a, iK$9, until, evidence$4);
      } else {
        var iK$11 = ((((iK$9 + until) | 0) >>> 1) | 0);
        this.U(a, iK$9, iK$11, evidence$4, sc$3, evidence$2$3);
        this.U(a, iK$11, until, evidence$4, sc$3, evidence$2$3);
        this.aW(a, iK$9, iK$11, until, evidence$4, sc$3);
      }
      this.aW(a, from, iK$9, until, evidence$4, sc$3);
    }
  } else if ((a instanceof $ac_C)) {
    if ((evidence$4 === $m_s_math_Ordering$Char$())) {
      $m_ju_Arrays$().s6(a, from, until);
    } else {
      var evidence$2$4 = $m_s_reflect_ManifestFactory$CharManifest$();
      if ((((until - from) | 0) < 32)) {
        this.aV(a, from, until, evidence$4);
      } else {
        var iK$12 = ((((from + until) | 0) >>> 1) | 0);
        var sc$4 = new $ac_C(((iK$12 - from) | 0));
        if ((((iK$12 - from) | 0) < 32)) {
          this.aV(a, from, iK$12, evidence$4);
        } else {
          var iK$13 = ((((from + iK$12) | 0) >>> 1) | 0);
          this.U(a, from, iK$13, evidence$4, sc$4, evidence$2$4);
          this.U(a, iK$13, iK$12, evidence$4, sc$4, evidence$2$4);
          this.aW(a, from, iK$13, iK$12, evidence$4, sc$4);
        }
        if ((((until - iK$12) | 0) < 32)) {
          this.aV(a, iK$12, until, evidence$4);
        } else {
          var iK$14 = ((((iK$12 + until) | 0) >>> 1) | 0);
          this.U(a, iK$12, iK$14, evidence$4, sc$4, evidence$2$4);
          this.U(a, iK$14, until, evidence$4, sc$4, evidence$2$4);
          this.aW(a, iK$12, iK$14, until, evidence$4, sc$4);
        }
        this.aW(a, from, iK$12, until, evidence$4, sc$4);
      }
    }
  } else if ((a instanceof $ac_B)) {
    if ((evidence$4 === $m_s_math_Ordering$Byte$())) {
      $m_ju_Arrays$().s5(a, from, until);
    } else {
      var evidence$2$5 = $m_s_reflect_ManifestFactory$ByteManifest$();
      if ((((until - from) | 0) < 32)) {
        this.aV(a, from, until, evidence$4);
      } else {
        var iK$15 = ((((from + until) | 0) >>> 1) | 0);
        var sc$5 = new $ac_B(((iK$15 - from) | 0));
        if ((((iK$15 - from) | 0) < 32)) {
          this.aV(a, from, iK$15, evidence$4);
        } else {
          var iK$16 = ((((from + iK$15) | 0) >>> 1) | 0);
          this.U(a, from, iK$16, evidence$4, sc$5, evidence$2$5);
          this.U(a, iK$16, iK$15, evidence$4, sc$5, evidence$2$5);
          this.aW(a, from, iK$16, iK$15, evidence$4, sc$5);
        }
        if ((((until - iK$15) | 0) < 32)) {
          this.aV(a, iK$15, until, evidence$4);
        } else {
          var iK$17 = ((((iK$15 + until) | 0) >>> 1) | 0);
          this.U(a, iK$15, iK$17, evidence$4, sc$5, evidence$2$5);
          this.U(a, iK$17, until, evidence$4, sc$5, evidence$2$5);
          this.aW(a, iK$15, iK$17, until, evidence$4, sc$5);
        }
        this.aW(a, from, iK$15, until, evidence$4, sc$5);
      }
    }
  } else if ((a instanceof $ac_S)) {
    if ((evidence$4 === $m_s_math_Ordering$Short$())) {
      $m_ju_Arrays$().s9(a, from, until);
    } else {
      var evidence$2$6 = $m_s_reflect_ManifestFactory$ShortManifest$();
      if ((((until - from) | 0) < 32)) {
        this.aV(a, from, until, evidence$4);
      } else {
        var iK$18 = ((((from + until) | 0) >>> 1) | 0);
        var sc$6 = new $ac_S(((iK$18 - from) | 0));
        if ((((iK$18 - from) | 0) < 32)) {
          this.aV(a, from, iK$18, evidence$4);
        } else {
          var iK$19 = ((((from + iK$18) | 0) >>> 1) | 0);
          this.U(a, from, iK$19, evidence$4, sc$6, evidence$2$6);
          this.U(a, iK$19, iK$18, evidence$4, sc$6, evidence$2$6);
          this.aW(a, from, iK$19, iK$18, evidence$4, sc$6);
        }
        if ((((until - iK$18) | 0) < 32)) {
          this.aV(a, iK$18, until, evidence$4);
        } else {
          var iK$20 = ((((iK$18 + until) | 0) >>> 1) | 0);
          this.U(a, iK$18, iK$20, evidence$4, sc$6, evidence$2$6);
          this.U(a, iK$20, until, evidence$4, sc$6, evidence$2$6);
          this.aW(a, iK$18, iK$20, until, evidence$4, sc$6);
        }
        this.aW(a, from, iK$18, until, evidence$4, sc$6);
      }
    }
  } else if ((a instanceof $ac_Z)) {
    if ((evidence$4 === $m_s_math_Ordering$Boolean$())) {
      this.rY(a, from, until);
    } else {
      var evidence$2$7 = $m_s_reflect_ManifestFactory$BooleanManifest$();
      if ((((until - from) | 0) < 32)) {
        this.aV(a, from, until, evidence$4);
      } else {
        var iK$21 = ((((from + until) | 0) >>> 1) | 0);
        var sc$7 = new $ac_Z(((iK$21 - from) | 0));
        if ((((iK$21 - from) | 0) < 32)) {
          this.aV(a, from, iK$21, evidence$4);
        } else {
          var iK$22 = ((((from + iK$21) | 0) >>> 1) | 0);
          this.U(a, from, iK$22, evidence$4, sc$7, evidence$2$7);
          this.U(a, iK$22, iK$21, evidence$4, sc$7, evidence$2$7);
          this.aW(a, from, iK$22, iK$21, evidence$4, sc$7);
        }
        if ((((until - iK$21) | 0) < 32)) {
          this.aV(a, iK$21, until, evidence$4);
        } else {
          var iK$23 = ((((iK$21 + until) | 0) >>> 1) | 0);
          this.U(a, iK$21, iK$23, evidence$4, sc$7, evidence$2$7);
          this.U(a, iK$23, until, evidence$4, sc$7, evidence$2$7);
          this.aW(a, iK$21, iK$23, until, evidence$4, sc$7);
        }
        this.aW(a, from, iK$21, until, evidence$4, sc$7);
      }
    }
  } else if ((a === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  } else {
    throw new $c_s_MatchError(a);
  }
});
var $d_s_util_Sorting$ = new $TypeData().i($c_s_util_Sorting$, "scala.util.Sorting$", ({
  jb: 1
}));
var $n_s_util_Sorting$;
function $m_s_util_Sorting$() {
  if ((!$n_s_util_Sorting$)) {
    $n_s_util_Sorting$ = new $c_s_util_Sorting$();
  }
  return $n_s_util_Sorting$;
}
function $f_s_util_control_NoStackTrace__fillInStackTrace__jl_Throwable($thiz) {
  return ($m_s_util_control_NoStackTrace$().nC ? $c_jl_Throwable.prototype.gq.call($thiz) : $thiz);
}
/** @constructor */
function $c_s_util_control_NoStackTrace$() {
  this.nC = false;
  this.nC = false;
}
$p = $c_s_util_control_NoStackTrace$.prototype = new $h_O();
$p.constructor = $c_s_util_control_NoStackTrace$;
/** @constructor */
function $h_s_util_control_NoStackTrace$() {
}
$h_s_util_control_NoStackTrace$.prototype = $p;
var $d_s_util_control_NoStackTrace$ = new $TypeData().i($c_s_util_control_NoStackTrace$, "scala.util.control.NoStackTrace$", ({
  jd: 1
}));
var $n_s_util_control_NoStackTrace$;
function $m_s_util_control_NoStackTrace$() {
  if ((!$n_s_util_control_NoStackTrace$)) {
    $n_s_util_control_NoStackTrace$ = new $c_s_util_control_NoStackTrace$();
  }
  return $n_s_util_control_NoStackTrace$;
}
/** @constructor */
function $c_s_util_control_NonFatal$() {
}
$p = $c_s_util_control_NonFatal$.prototype = new $h_O();
$p.constructor = $c_s_util_control_NonFatal$;
/** @constructor */
function $h_s_util_control_NonFatal$() {
}
$h_s_util_control_NonFatal$.prototype = $p;
$p.iR = (function(t) {
  matchAlts1: {
    matchAlts2: {
      if ((t instanceof $c_jl_VirtualMachineError)) {
        break matchAlts2;
      }
      if (false) {
        break matchAlts2;
      }
      if (false) {
        break matchAlts2;
      }
      if (false) {
        break matchAlts2;
      }
      if (false) {
        break matchAlts2;
      }
      break matchAlts1;
    }
    return false;
  }
  return true;
});
$p.sn = (function(t) {
  return (this.iR(t) ? new $c_s_Some(t) : $m_s_None$());
});
var $d_s_util_control_NonFatal$ = new $TypeData().i($c_s_util_control_NonFatal$, "scala.util.control.NonFatal$", ({
  je: 1
}));
var $n_s_util_control_NonFatal$;
function $m_s_util_control_NonFatal$() {
  if ((!$n_s_util_control_NonFatal$)) {
    $n_s_util_control_NonFatal$ = new $c_s_util_control_NonFatal$();
  }
  return $n_s_util_control_NonFatal$;
}
/** @constructor */
function $c_s_util_hashing_MurmurHash3() {
}
$p = $c_s_util_hashing_MurmurHash3.prototype = new $h_O();
$p.constructor = $c_s_util_hashing_MurmurHash3;
/** @constructor */
function $h_s_util_hashing_MurmurHash3() {
}
$h_s_util_hashing_MurmurHash3.prototype = $p;
$p.x = (function(hash, data) {
  var h = this.eq(hash, data);
  var i = h;
  h = ((i << 13) | ((i >>> 19) | 0));
  return ((Math.imul(5, h) - 430675100) | 0);
});
$p.eq = (function(hash, data) {
  var k = data;
  k = Math.imul((-862048943), k);
  var i = k;
  k = ((i << 15) | ((i >>> 17) | 0));
  k = Math.imul(461845907, k);
  return (hash ^ k);
});
$p.bd = (function(hash, length) {
  return this.hT((hash ^ length));
});
$p.hT = (function(hash) {
  var h = hash;
  h = (h ^ ((h >>> 16) | 0));
  h = Math.imul((-2048144789), h);
  h = (h ^ ((h >>> 13) | 0));
  h = Math.imul((-1028477387), h);
  h = (h ^ ((h >>> 16) | 0));
  return h;
});
$p.p2 = (function(x, y, seed) {
  var h = seed;
  h = this.x(h, $f_T__hashCode__I("Tuple2"));
  h = this.x(h, x);
  h = this.x(h, y);
  return this.bd(h, 2);
});
$p.dr = (function(x, seed, ignorePrefix) {
  var arr = x.bE();
  if ((arr === 0)) {
    return ((!ignorePrefix) ? $f_T__hashCode__I(x.bG()) : seed);
  } else {
    var h = seed;
    if ((!ignorePrefix)) {
      h = this.x(h, $f_T__hashCode__I(x.bG()));
    }
    var i = 0;
    while ((i < arr)) {
      h = this.x(h, $m_sr_Statics$().y(x.bF(i)));
      i = ((1 + i) | 0);
    }
    return this.bd(h, arr);
  }
});
$p.gA = (function(xs, seed) {
  var a = 0;
  var b = 0;
  var n = 0;
  var c = 1;
  var iterator = xs.c();
  while (iterator.k()) {
    var x = iterator.f();
    var h = $m_sr_Statics$().y(x);
    a = ((a + h) | 0);
    b = (b ^ h);
    c = Math.imul(c, (1 | h));
    n = ((1 + n) | 0);
  }
  var h$2 = seed;
  h$2 = this.x(h$2, a);
  h$2 = this.x(h$2, b);
  h$2 = this.eq(h$2, c);
  return this.bd(h$2, n);
});
$p.rD = (function(xs, seed) {
  var it = xs.c();
  var h = seed;
  if ((!it.k())) {
    return this.bd(h, 0);
  }
  var x0 = it.f();
  if ((!it.k())) {
    return this.bd(this.x(h, $m_sr_Statics$().y(x0)), 1);
  }
  var x1 = it.f();
  var initial = $m_sr_Statics$().y(x0);
  h = this.x(h, initial);
  var h0 = h;
  var prev = $m_sr_Statics$().y(x1);
  var rangeDiff = ((prev - initial) | 0);
  var i = 2;
  while (it.k()) {
    h = this.x(h, prev);
    var hash = $m_sr_Statics$().y(it.f());
    if (((rangeDiff !== ((hash - prev) | 0)) || (rangeDiff === 0))) {
      h = this.x(h, hash);
      i = ((1 + i) | 0);
      while (it.k()) {
        h = this.x(h, $m_sr_Statics$().y(it.f()));
        i = ((1 + i) | 0);
      }
      return this.bd(h, i);
    }
    prev = hash;
    i = ((1 + i) | 0);
  }
  return this.hT(this.x(this.x(h0, rangeDiff), prev));
});
$p.bo = (function(a, seed) {
  var h = seed;
  var l = $m_jl_reflect_Array$().b8(a);
  switch (l) {
    case 0: {
      return this.bd(h, 0);
      break;
    }
    case 1: {
      return this.bd(this.x(h, $m_sr_Statics$().y($m_sr_ScalaRunTime$().aM(a, 0))), 1);
      break;
    }
    default: {
      var initial = $m_sr_Statics$().y($m_sr_ScalaRunTime$().aM(a, 0));
      h = this.x(h, initial);
      var h0 = h;
      var prev = $m_sr_Statics$().y($m_sr_ScalaRunTime$().aM(a, 1));
      var rangeDiff = ((prev - initial) | 0);
      var i = 2;
      while ((i < l)) {
        h = this.x(h, prev);
        var hash = $m_sr_Statics$().y($m_sr_ScalaRunTime$().aM(a, i));
        if (((rangeDiff !== ((hash - prev) | 0)) || (rangeDiff === 0))) {
          h = this.x(h, hash);
          i = ((1 + i) | 0);
          while ((i < l)) {
            h = this.x(h, $m_sr_Statics$().y($m_sr_ScalaRunTime$().aM(a, i)));
            i = ((1 + i) | 0);
          }
          return this.bd(h, l);
        }
        prev = hash;
        i = ((1 + i) | 0);
      }
      return this.hT(this.x(this.x(h0, rangeDiff), prev));
    }
  }
});
$p.rL = (function(start, step, last, seed) {
  return this.hT(this.x(this.x(this.x(seed, start), step), last));
});
$p.rb = (function(a, seed) {
  var h = seed;
  var l = a.l();
  switch (l) {
    case 0: {
      return this.bd(h, 0);
      break;
    }
    case 1: {
      return this.bd(this.x(h, $m_sr_Statics$().y(a.r(0))), 1);
      break;
    }
    default: {
      var initial = $m_sr_Statics$().y(a.r(0));
      h = this.x(h, initial);
      var h0 = h;
      var prev = $m_sr_Statics$().y(a.r(1));
      var rangeDiff = ((prev - initial) | 0);
      var i = 2;
      while ((i < l)) {
        h = this.x(h, prev);
        var hash = $m_sr_Statics$().y(a.r(i));
        if (((rangeDiff !== ((hash - prev) | 0)) || (rangeDiff === 0))) {
          h = this.x(h, hash);
          i = ((1 + i) | 0);
          while ((i < l)) {
            h = this.x(h, $m_sr_Statics$().y(a.r(i)));
            i = ((1 + i) | 0);
          }
          return this.bd(h, l);
        }
        prev = hash;
        i = ((1 + i) | 0);
      }
      return this.hT(this.x(this.x(h0, rangeDiff), prev));
    }
  }
});
$p.ro = (function(xs, seed) {
  var n = 0;
  var h = seed;
  var rangeState = 0;
  var rangeDiff = 0;
  var prev = 0;
  var initial = 0;
  var elems = xs;
  while ((!elems.g())) {
    var head = elems.m();
    var tail = elems.o();
    var hash = $m_sr_Statics$().y(head);
    h = this.x(h, hash);
    switch (rangeState) {
      case 0: {
        initial = hash;
        rangeState = 1;
        break;
      }
      case 1: {
        rangeDiff = ((hash - prev) | 0);
        rangeState = 2;
        break;
      }
      case 2: {
        if (((rangeDiff !== ((hash - prev) | 0)) || (rangeDiff === 0))) {
          rangeState = 3;
        }
        break;
      }
    }
    prev = hash;
    n = ((1 + n) | 0);
    elems = tail;
  }
  return ((rangeState === 2) ? this.rL(initial, rangeDiff, prev, seed) : this.bd(h, n));
});
/** @constructor */
function $c_Ldotty_tools_scaladoc_RecentQueryStorage$() {
  this.i0 = null;
  this.fX = null;
  this.hZ = false;
  this.lm = 0;
  $ct_Ldotty_tools_scaladoc_SafeLocalStorage__T__sjs_js_Any__(this, "__RECENT__QUERIES__", []);
  this.lm = 3;
}
$p = $c_Ldotty_tools_scaladoc_RecentQueryStorage$.prototype = new $h_Ldotty_tools_scaladoc_SafeLocalStorage();
$p.constructor = $c_Ldotty_tools_scaladoc_RecentQueryStorage$;
/** @constructor */
function $h_Ldotty_tools_scaladoc_RecentQueryStorage$() {
}
$h_Ldotty_tools_scaladoc_RecentQueryStorage$.prototype = $p;
$p.pM = (function(rq) {
  var array = this.iW();
  _return: {
    var len = (array.length | 0);
    var i = 0;
    while ((i < len)) {
      var x0 = array[i];
      var this$4 = x0.query;
      var s = rq.query;
      if (((this$4.indexOf(s) | 0) !== (-1))) {
        var $x_1 = i;
        break _return;
      }
      i = ((1 + i) | 0);
    }
    var $x_1 = (-1);
  }
  if ((!($x_1 >= 0))) {
    var array$1 = this.iW();
    var res = [];
    var len$1 = (array$1.length | 0);
    var i$1 = 0;
    while ((i$1 < len$1)) {
      var x0$1 = array$1[i$1];
      var this$9 = rq.query;
      var s$1 = x0$1.query;
      if ((!((this$9.indexOf(s$1) | 0) !== (-1)))) {
        (res.push(x0$1) | 0);
      }
      i$1 = ((1 + i$1) | 0);
    }
    var newData = $m_sjs_js_ArrayOps$().pY(res, rq);
    var array$4 = $m_sjs_js_ArrayOps$().qr($m_sjs_js_ArrayOps$().rR($m_sjs_js_ArrayOps$().sb(newData, new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$2$2) => (+_$2$2.timestamp))), $m_s_math_Ordering$DeprecatedDoubleOrdering$())), new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$3$2) => _$3$2.query)));
    var n = this.lm;
    this.s1(array$4.slice(0, ((n > 0) ? n : 0)));
  }
});
var $d_Ldotty_tools_scaladoc_RecentQueryStorage$ = new $TypeData().i($c_Ldotty_tools_scaladoc_RecentQueryStorage$, "dotty.tools.scaladoc.RecentQueryStorage$", ({
  db: 1,
  dc: 1
}));
var $n_Ldotty_tools_scaladoc_RecentQueryStorage$;
function $m_Ldotty_tools_scaladoc_RecentQueryStorage$() {
  if ((!$n_Ldotty_tools_scaladoc_RecentQueryStorage$)) {
    $n_Ldotty_tools_scaladoc_RecentQueryStorage$ = new $c_Ldotty_tools_scaladoc_RecentQueryStorage$();
  }
  return $n_Ldotty_tools_scaladoc_RecentQueryStorage$;
}
/** @constructor */
function $c_jl_Character$() {
  this.jj = null;
  $n_jl_Character$ = this;
  this.jj = $constArrUDiffs_I(67, "1C]4m6m=c4]4]4]4]4]4]4]4]4]3g4]2m9]2m1Jm1m9s4g5mm6]3]4mm12>mEm1m6m1]3]=]DI]1<m24mIs4g2c4w9];]4]<]3m3m=m3mH]8]2m=mBHm3]4mK3{gggg2:g=m@]13]4E]");
}
$p = $c_jl_Character$.prototype = new $h_O();
$p.constructor = $c_jl_Character$;
/** @constructor */
function $h_jl_Character$() {
}
$h_jl_Character$.prototype = $p;
$p.hW = (function(codePoint) {
  if (((codePoint >>> 0) > 1114111)) {
    throw $ct_jl_IllegalArgumentException__(new $c_jl_IllegalArgumentException());
  }
  return String.fromCodePoint(codePoint);
});
$p.qq = (function(codePoint, radix) {
  if ((codePoint < 256)) {
    var value = (((((codePoint - 48) | 0) >>> 0) <= 9) ? ((codePoint - 48) | 0) : (((((codePoint - 65) | 0) >>> 0) <= 25) ? ((codePoint - 55) | 0) : (((((codePoint - 97) | 0) >>> 0) <= 25) ? ((codePoint - 87) | 0) : (-1))));
  } else if (((((codePoint - 65313) | 0) >>> 0) <= 25)) {
    var value = ((codePoint - 65303) | 0);
  } else if (((((codePoint - 65345) | 0) >>> 0) <= 25)) {
    var value = ((codePoint - 65335) | 0);
  } else {
    var p = $m_ju_Arrays$().q7(this.jj, codePoint);
    var zeroCodePointIndex = ((p < 0) ? (((-2) - p) | 0) : p);
    if ((zeroCodePointIndex < 0)) {
      var value = (-1);
    } else {
      var v = ((codePoint - this.jj.a[zeroCodePointIndex]) | 0);
      var value = ((v > 9) ? (-1) : v);
    }
  }
  return ((value < radix) ? value : (-1));
});
$p.jb = (function(codePoint) {
  switch (codePoint) {
    case 8115:
    case 8131:
    case 8179: {
      return ((9 + codePoint) | 0);
      break;
    }
    default: {
      if (((((codePoint - 8064) | 0) >>> 0) <= 47)) {
        return (8 | codePoint);
      } else {
        var this$1 = this.hW(codePoint);
        var upperChars = this$1.toUpperCase();
        switch (upperChars.length) {
          case 1: {
            return upperChars.charCodeAt(0);
            break;
          }
          case 2: {
            var high = upperChars.charCodeAt(0);
            var low = upperChars.charCodeAt(1);
            return ((((-67044352) & ((high << 16) | low)) === (-671032320)) ? ((((64 + (1023 & high)) | 0) << 10) | (1023 & low)) : codePoint);
            break;
          }
          default: {
            return codePoint;
          }
        }
      }
    }
  }
});
$p.ja = (function(ch) {
  return (65535 & this.hV(ch));
});
$p.hV = (function(codePoint) {
  if ((codePoint === 304)) {
    return 105;
  } else {
    var this$1 = this.hW(codePoint);
    var lowerChars = this$1.toLowerCase();
    switch (lowerChars.length) {
      case 1: {
        return lowerChars.charCodeAt(0);
        break;
      }
      case 2: {
        var high = lowerChars.charCodeAt(0);
        var low = lowerChars.charCodeAt(1);
        return ((((-67044352) & ((high << 16) | low)) === (-671032320)) ? ((((64 + (1023 & high)) | 0) << 10) | (1023 & low)) : codePoint);
        break;
      }
      default: {
        return codePoint;
      }
    }
  }
});
var $d_jl_Character$ = new $TypeData().i($c_jl_Character$, "java.lang.Character$", ({
  dr: 1,
  a: 1
}));
var $n_jl_Character$;
function $m_jl_Character$() {
  if ((!$n_jl_Character$)) {
    $n_jl_Character$ = new $c_jl_Character$();
  }
  return $n_jl_Character$;
}
/** @constructor */
function $c_jl_Double$() {
}
$p = $c_jl_Double$.prototype = new $h_O();
$p.constructor = $c_jl_Double$;
/** @constructor */
function $h_jl_Double$() {
}
$h_jl_Double$.prototype = $p;
$p.kq = (function(a, b) {
  if ((a !== a)) {
    return ((b === b) | 0);
  } else if ((b !== b)) {
    return (-1);
  } else if ((a === b)) {
    if ((a === 0.0)) {
      var ainf = (1.0 / a);
      return ((ainf === (1.0 / b)) ? 0 : ((ainf < 0.0) ? (-1) : 1));
    } else {
      return 0;
    }
  } else {
    return ((a < b) ? (-1) : 1);
  }
});
var $d_jl_Double$ = new $TypeData().i($c_jl_Double$, "java.lang.Double$", ({
  du: 1,
  a: 1
}));
var $n_jl_Double$;
function $m_jl_Double$() {
  if ((!$n_jl_Double$)) {
    $n_jl_Double$ = new $c_jl_Double$();
  }
  return $n_jl_Double$;
}
/** @constructor */
function $c_jl_Integer$() {
}
$p = $c_jl_Integer$.prototype = new $h_O();
$p.constructor = $c_jl_Integer$;
/** @constructor */
function $h_jl_Integer$() {
}
$h_jl_Integer$.prototype = $p;
$p.hP = (function(s) {
  throw new $c_jl_NumberFormatException((("For input string: \"" + s) + "\""));
});
$p.kU = (function(s, radix, overflowBarrier) {
  if ((s === null)) {
    $m_jl_Integer$().hP(s);
  }
  var len = s.length;
  if ((len === 0)) {
    $m_jl_Integer$().hP(s);
  }
  var character = $m_jl_Character$();
  var firstChar = s.charCodeAt(0);
  var negative = (firstChar === 45);
  var sign = (negative ? (-1) : 0);
  var i = ((negative || (firstChar === 43)) | 0);
  if ((i >= len)) {
    $m_jl_Integer$().hP(s);
  }
  var java$lang$IntFloatBits$Int32Box$$value = 0;
  java$lang$IntFloatBits$Int32Box$$value = 0;
  while ((i !== len)) {
    var x = character.qq(s.charCodeAt(i), radix);
    if (((x < 0) || ((java$lang$IntFloatBits$Int32Box$$value >>> 0) > (overflowBarrier >>> 0)))) {
      $m_jl_Integer$().hP(s);
    }
    var x$2 = java$lang$IntFloatBits$Int32Box$$value;
    var x$3 = Math.imul(x$2, radix);
    var v = ((x$3 + x) | 0);
    java$lang$IntFloatBits$Int32Box$$value = v;
    i = ((1 + i) | 0);
  }
  if (((java$lang$IntFloatBits$Int32Box$$value >>> 0) > (((2147483647 - sign) | 0) >>> 0))) {
    $m_jl_Integer$().hP(s);
  }
  return (((java$lang$IntFloatBits$Int32Box$$value ^ sign) - sign) | 0);
});
$p.bR = (function(i) {
  var t1 = ((i - (1431655765 & (i >> 1))) | 0);
  var t2 = (((858993459 & t1) + (858993459 & (t1 >> 2))) | 0);
  return (Math.imul(16843009, (252645135 & ((t2 + (t2 >> 4)) | 0))) >> 24);
});
var $d_jl_Integer$ = new $TypeData().i($c_jl_Integer$, "java.lang.Integer$", ({
  dv: 1,
  a: 1
}));
var $n_jl_Integer$;
function $m_jl_Integer$() {
  if ((!$n_jl_Integer$)) {
    $n_jl_Integer$ = new $c_jl_Integer$();
  }
  return $n_jl_Integer$;
}
/** @constructor */
function $c_jl_Long$() {
}
$p = $c_jl_Long$.prototype = new $h_O();
$p.constructor = $c_jl_Long$;
/** @constructor */
function $h_jl_Long$() {
}
$h_jl_Long$.prototype = $p;
$p.ov = (function(lo, hi) {
  if ((hi !== 0)) {
    var $x_1 = (hi >>> 0.0).toString(16);
    var s = (lo >>> 0.0).toString(16);
    var beginIndex = s.length;
    return ($x_1 + (("" + "00000000".substring(beginIndex)) + s));
  } else {
    return (lo >>> 0.0).toString(16);
  }
});
$p.rh = (function(lo, hi) {
  var lp = (1073741823 & lo);
  var mp = (1073741823 & ((((lo >>> 30) | 0) + (hi << 2)) | 0));
  var hp = ((hi >>> 28) | 0);
  if ((hp !== 0)) {
    var $x_2 = (hp >>> 0.0).toString(8);
    var s = (mp >>> 0.0).toString(8);
    var beginIndex = s.length;
    var $x_1 = "0000000000".substring(beginIndex);
    var s$1 = (lp >>> 0.0).toString(8);
    var beginIndex$1 = s$1.length;
    return (($x_2 + (("" + $x_1) + s)) + (("" + "0000000000".substring(beginIndex$1)) + s$1));
  } else if ((mp !== 0)) {
    var $x_3 = (mp >>> 0.0).toString(8);
    var s$2 = (lp >>> 0.0).toString(8);
    var beginIndex$2 = s$2.length;
    return ($x_3 + (("" + "0000000000".substring(beginIndex$2)) + s$2));
  } else {
    return (lp >>> 0.0).toString(8);
  }
});
var $d_jl_Long$ = new $TypeData().i($c_jl_Long$, "java.lang.Long$", ({
  dA: 1,
  a: 1
}));
var $n_jl_Long$;
function $m_jl_Long$() {
  if ((!$n_jl_Long$)) {
    $n_jl_Long$ = new $c_jl_Long$();
  }
  return $n_jl_Long$;
}
/** @constructor */
function $c_jl_Number() {
}
$p = $c_jl_Number.prototype = new $h_O();
$p.constructor = $c_jl_Number;
/** @constructor */
function $h_jl_Number() {
}
$h_jl_Number.prototype = $p;
function $is_jl_Number(obj) {
  return (((obj instanceof $c_jl_Number) || ((typeof obj) === "number")) || (obj instanceof $Long));
}
function $isArrayOf_jl_Number(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.ai)));
}
/** @constructor */
function $c_jl_StackTraceElement(declaringClass, methodName, fileName, lineNumber, columnNumber) {
  this.g0 = null;
  this.gJ = null;
  this.g1 = null;
  this.g2 = 0;
  this.fZ = 0;
  this.g0 = declaringClass;
  this.gJ = methodName;
  this.g1 = fileName;
  this.g2 = lineNumber;
  this.fZ = columnNumber;
}
$p = $c_jl_StackTraceElement.prototype = new $h_O();
$p.constructor = $c_jl_StackTraceElement;
/** @constructor */
function $h_jl_StackTraceElement() {
}
$h_jl_StackTraceElement.prototype = $p;
$p.p = (function(that) {
  return ((that instanceof $c_jl_StackTraceElement) && (((((this.g1 === that.g1) && (this.g2 === that.g2)) && (this.fZ === that.fZ)) && (this.g0 === that.g0)) && (this.gJ === that.gJ)));
});
$p.t = (function() {
  var result = "";
  if ((this.g0 !== "<jscode>")) {
    result = ((("" + result) + this.g0) + ".");
  }
  result = (("" + result) + this.gJ);
  if ((this.g1 === null)) {
    result = (result + "(Unknown Source)");
  } else {
    result = ((result + "(") + this.g1);
    if ((this.g2 >= 0)) {
      result = ((result + ":") + this.g2);
      if ((this.fZ >= 0)) {
        result = ((result + ":") + this.fZ);
      }
    }
    result = (result + ")");
  }
  return result;
});
$p.u = (function() {
  return (((($f_T__hashCode__I(this.g0) ^ $f_T__hashCode__I(this.gJ)) ^ $f_T__hashCode__I(this.g1)) ^ this.g2) ^ this.fZ);
});
function $isArrayOf_jl_StackTraceElement(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.by)));
}
var $d_jl_StackTraceElement = new $TypeData().i($c_jl_StackTraceElement, "java.lang.StackTraceElement", ({
  by: 1,
  a: 1
}));
/** @constructor */
function $c_jl_String$() {
}
$p = $c_jl_String$.prototype = new $h_O();
$p.constructor = $c_jl_String$;
/** @constructor */
function $h_jl_String$() {
}
$h_jl_String$.prototype = $p;
$p.rz = (function(value, offset, count) {
  var endOffset = ((offset + count) | 0);
  var result = "";
  var i = offset;
  while ((i !== endOffset)) {
    result = (result + ("" + $cToS(value.a[i])));
    i = ((1 + i) | 0);
  }
  return result;
});
$p.qQ = (function(format, args) {
  return $ct_ju_Formatter__(new $c_ju_Formatter()).qR(format, args).t();
});
var $d_jl_String$ = new $TypeData().i($c_jl_String$, "java.lang.String$", ({
  dH: 1,
  a: 1
}));
var $n_jl_String$;
function $m_jl_String$() {
  if ((!$n_jl_String$)) {
    $n_jl_String$ = new $c_jl_String$();
  }
  return $n_jl_String$;
}
function $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, s, e, enableSuppression, writableStackTrace) {
  $thiz.lK = s;
  $thiz.i4 = e;
  $thiz.lL = writableStackTrace;
  if (writableStackTrace) {
    $thiz.gq();
  }
  return $thiz;
}
class $c_jl_Throwable extends Error {
  constructor() {
    super();
    this.lK = null;
    this.i4 = null;
    this.lL = false;
    this.lJ = null;
    this.fa = null;
  }
  c1() {
    return this.lK;
  }
  gq() {
    var reference = ((this instanceof $c_sjs_js_JavaScriptException) ? this.di : this);
    this.lJ = ((Object.prototype.toString.call(reference) === "[object Error]") ? reference : (((Error.captureStackTrace === (void 0)) || (!(!Object.isSealed(this)))) ? new Error() : (Error.captureStackTrace(this), this)));
    return this;
  }
  kP() {
    if ((this.fa === null)) {
      if (this.lL) {
        this.fa = $m_jl_StackTrace$().qF(this.lJ);
      } else {
        this.fa = new ($d_jl_StackTraceElement.r().C)(0);
      }
    }
    return this.fa;
  }
  hR(s) {
    this.kP();
    var t = this.t();
    s.fI(t);
    if ((this.fa.a.length !== 0)) {
      var i = 0;
      while ((i < this.fa.a.length)) {
        var t$1 = ("  at " + this.fa.a[i]);
        s.fI(t$1);
        i = ((1 + i) | 0);
      }
    } else {
      s.fI("  <no stack trace available>");
    }
    var wCause = this;
    while (((wCause !== wCause.i4) && (wCause.i4 !== null))) {
      var parentTrace = wCause.kP();
      wCause = wCause.i4;
      var thisTrace = wCause.kP();
      var thisLength = thisTrace.a.length;
      var parentLength = parentTrace.a.length;
      var t$2 = ("Caused by: " + wCause);
      s.fI(t$2);
      if ((thisLength !== 0)) {
        var sameFrameCount = 0;
        while (true) {
          if (((sameFrameCount < thisLength) && (sameFrameCount < parentLength))) {
            var x = thisTrace.a[((((thisLength - sameFrameCount) | 0) - 1) | 0)];
            var x$2 = parentTrace.a[((((parentLength - sameFrameCount) | 0) - 1) | 0)];
            var $x_1 = ((x === null) ? (x$2 === null) : x.p(x$2));
          } else {
            var $x_1 = false;
          }
          if ($x_1) {
            sameFrameCount = ((1 + sameFrameCount) | 0);
          } else {
            break;
          }
        }
        if ((sameFrameCount > 0)) {
          sameFrameCount = ((sameFrameCount - 1) | 0);
        }
        var lengthToPrint = ((thisLength - sameFrameCount) | 0);
        var i$2 = 0;
        while ((i$2 < lengthToPrint)) {
          var t$3 = ("  at " + thisTrace.a[i$2]);
          s.fI(t$3);
          i$2 = ((1 + i$2) | 0);
        }
        if ((sameFrameCount > 0)) {
          var t$4 = (("  ... " + sameFrameCount) + " more");
          s.fI(t$4);
        }
      } else {
        s.fI("  <no stack trace available>");
      }
    }
  }
  t() {
    var className = $objectClassName(this);
    var message = this.c1();
    return ((message === null) ? className : ((className + ": ") + message));
  }
  u() {
    return $c_O.prototype.u.call(this);
  }
  p(that) {
    return $c_O.prototype.p.call(this, that);
  }
  get "message"() {
    var m = this.c1();
    return ((m === null) ? "" : m);
  }
  get "name"() {
    return $objectClassName(this);
  }
  "toString"() {
    return this.t();
  }
}
function $isArrayOf_jl_Throwable(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.r)));
}
/** @constructor */
function $c_ju_Arrays$NaturalComparator$() {
}
$p = $c_ju_Arrays$NaturalComparator$.prototype = new $h_O();
$p.constructor = $c_ju_Arrays$NaturalComparator$;
/** @constructor */
function $h_ju_Arrays$NaturalComparator$() {
}
$h_ju_Arrays$NaturalComparator$.prototype = $p;
$p.ax = (function(o1, o2) {
  return $dp_compareTo__O__I(o1, o2);
});
var $d_ju_Arrays$NaturalComparator$ = new $TypeData().i($c_ju_Arrays$NaturalComparator$, "java.util.Arrays$NaturalComparator$", ({
  dU: 1,
  P: 1
}));
var $n_ju_Arrays$NaturalComparator$;
function $m_ju_Arrays$NaturalComparator$() {
  if ((!$n_ju_Arrays$NaturalComparator$)) {
    $n_ju_Arrays$NaturalComparator$ = new $c_ju_Arrays$NaturalComparator$();
  }
  return $n_ju_Arrays$NaturalComparator$;
}
/** @constructor */
function $c_ju_Formatter$RootLocaleInfo$() {
}
$p = $c_ju_Formatter$RootLocaleInfo$.prototype = new $h_ju_Formatter$LocaleInfo();
$p.constructor = $c_ju_Formatter$RootLocaleInfo$;
/** @constructor */
function $h_ju_Formatter$RootLocaleInfo$() {
}
$h_ju_Formatter$RootLocaleInfo$.prototype = $p;
var $d_ju_Formatter$RootLocaleInfo$ = new $TypeData().i($c_ju_Formatter$RootLocaleInfo$, "java.util.Formatter$RootLocaleInfo$", ({
  e2: 1,
  e1: 1
}));
var $n_ju_Formatter$RootLocaleInfo$;
function $m_ju_Formatter$RootLocaleInfo$() {
  if ((!$n_ju_Formatter$RootLocaleInfo$)) {
    $n_ju_Formatter$RootLocaleInfo$ = new $c_ju_Formatter$RootLocaleInfo$();
  }
  return $n_ju_Formatter$RootLocaleInfo$;
}
/** @constructor */
function $c_ju_concurrent_TimeUnit$() {
  this.m8 = null;
  this.m5 = null;
  this.m6 = null;
  this.m9 = null;
  this.m7 = null;
  this.m4 = null;
  this.m3 = null;
  $n_ju_concurrent_TimeUnit$ = this;
  this.m8 = new $c_ju_concurrent_TimeUnit$$anon$1();
  this.m5 = new $c_ju_concurrent_TimeUnit$$anon$2();
  this.m6 = new $c_ju_concurrent_TimeUnit$$anon$3();
  this.m9 = new $c_ju_concurrent_TimeUnit$$anon$4();
  this.m7 = new $c_ju_concurrent_TimeUnit$$anon$5();
  this.m4 = new $c_ju_concurrent_TimeUnit$$anon$6();
  this.m3 = new $c_ju_concurrent_TimeUnit$$anon$7();
}
$p = $c_ju_concurrent_TimeUnit$.prototype = new $h_O();
$p.constructor = $c_ju_concurrent_TimeUnit$;
/** @constructor */
function $h_ju_concurrent_TimeUnit$() {
}
$h_ju_concurrent_TimeUnit$.prototype = $p;
$p.dW = (function(a_$_lo, a_$_hi, b_$_lo, b_$_hi, max_$_lo, max_$_hi) {
  if (((a_$_hi === max_$_hi) ? ((a_$_lo >>> 0) > (max_$_lo >>> 0)) : (a_$_hi > max_$_hi))) {
    return $bL((-1), 2147483647);
  } else {
    var lo = ((-max_$_lo) | 0);
    var hi = ((((-max_$_hi) | 0) - ((lo !== 0) | 0)) | 0);
    if (((a_$_hi === hi) ? ((a_$_lo >>> 0) < (lo >>> 0)) : (a_$_hi < hi))) {
      return $bL(1, (-2147483648));
    } else {
      var a0 = (65535 & a_$_lo);
      var a1 = ((a_$_lo >>> 16) | 0);
      var b0 = (65535 & b_$_lo);
      var b1 = ((b_$_lo >>> 16) | 0);
      var a0b0 = Math.imul(a0, b0);
      var a1b0 = Math.imul(a1, b0);
      var a0b1 = Math.imul(a0, b1);
      var lo$1 = ((a0b0 + (((a1b0 + a0b1) | 0) << 16)) | 0);
      var c1part = ((((a0b0 >>> 16) | 0) + a0b1) | 0);
      var hi$1 = ((((((((Math.imul(a_$_lo, b_$_hi) + Math.imul(a_$_hi, b_$_lo)) | 0) + Math.imul(a1, b1)) | 0) + ((c1part >>> 16) | 0)) | 0) + (((((65535 & c1part) + a1b0) | 0) >>> 16) | 0)) | 0);
      return $bL(lo$1, hi$1);
    }
  }
});
var $d_ju_concurrent_TimeUnit$ = new $TypeData().i($c_ju_concurrent_TimeUnit$, "java.util.concurrent.TimeUnit$", ({
  ee: 1,
  a: 1
}));
var $n_ju_concurrent_TimeUnit$;
function $m_ju_concurrent_TimeUnit$() {
  if ((!$n_ju_concurrent_TimeUnit$)) {
    $n_ju_concurrent_TimeUnit$ = new $c_ju_concurrent_TimeUnit$();
  }
  return $n_ju_concurrent_TimeUnit$;
}
function $ct_ju_concurrent_atomic_AtomicReference__O__($thiz, value) {
  $thiz.ab = value;
  return $thiz;
}
/** @constructor */
function $c_ju_concurrent_atomic_AtomicReference() {
  this.ab = null;
}
$p = $c_ju_concurrent_atomic_AtomicReference.prototype = new $h_O();
$p.constructor = $c_ju_concurrent_atomic_AtomicReference;
/** @constructor */
function $h_ju_concurrent_atomic_AtomicReference() {
}
$h_ju_concurrent_atomic_AtomicReference.prototype = $p;
$p.hD = (function(expect, update) {
  if (Object.is(expect, this.ab)) {
    this.ab = update;
    return true;
  } else {
    return false;
  }
});
$p.t = (function() {
  return ("" + this.ab);
});
/** @constructor */
function $c_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$() {
}
$p = $c_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$.prototype = new $h_O();
$p.constructor = $c_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$;
/** @constructor */
function $h_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$() {
}
$h_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$.prototype = $p;
$p.dt = (function(a, i, v) {
  a.a[i] = v;
});
$p.c0 = (function(a, i) {
  return a.a[i];
});
var $d_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$ = new $TypeData().i($c_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$, "java.util.internal.GenericArrayOps$ReusableAnyRefArrayOps$", ({
  eq: 1,
  ar: 1
}));
var $n_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$;
function $m_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$() {
  if ((!$n_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$)) {
    $n_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$ = new $c_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$();
  }
  return $n_ju_internal_GenericArrayOps$ReusableAnyRefArrayOps$;
}
function $p_ju_regex_Matcher__resetMatch__ju_regex_Matcher($thiz) {
  $thiz.i6 = 0;
  $thiz.fc = null;
  return $thiz;
}
function $p_ju_regex_Matcher__ensureLastMatch__O($thiz) {
  if (($thiz.fc === null)) {
    throw $ct_jl_IllegalStateException__T__(new $c_jl_IllegalStateException(), "No match available");
  }
  return $thiz.fc;
}
/** @constructor */
function $c_ju_regex_Matcher(pattern0, input0) {
  this.gK = null;
  this.ma = null;
  this.mb = 0;
  this.i5 = null;
  this.i6 = 0;
  this.fc = null;
  this.gK = pattern0;
  this.ma = input0;
  this.mb = 0;
  this.i5 = this.ma;
  this.i6 = 0;
  this.fc = null;
}
$p = $c_ju_regex_Matcher.prototype = new $h_O();
$p.constructor = $c_ju_regex_Matcher;
/** @constructor */
function $h_ju_regex_Matcher() {
}
$h_ju_regex_Matcher.prototype = $p;
$p.rv = (function() {
  $p_ju_regex_Matcher__resetMatch__ju_regex_Matcher(this);
  this.fc = this.gK.qE(this.i5);
  return (this.fc !== null);
});
$p.qK = (function() {
  var this$1 = this.gK;
  var input = this.i5;
  var start = this.i6;
  var mtch = this$1.rl(input, start);
  var end = (this$1.js.lastIndex | 0);
  this.i6 = ((mtch !== null) ? ((end === (mtch.index | 0)) ? ((1 + end) | 0) : end) : ((1 + this.i5.length) | 0));
  this.fc = mtch;
  return (mtch !== null);
});
$p.oW = (function() {
  return ((($p_ju_regex_Matcher__ensureLastMatch__O(this).index | 0) + this.mb) | 0);
});
$p.o9 = (function() {
  return ((this.oW() + this.r5().length) | 0);
});
$p.r5 = (function() {
  return $p_ju_regex_Matcher__ensureLastMatch__O(this)[0];
});
$p.r4 = (function(group) {
  var x = $p_ju_regex_Matcher__ensureLastMatch__O(this)[this.gK.rC(group)];
  return ((x !== (void 0)) ? x : null);
});
var $d_ju_regex_Matcher = new $TypeData().i($c_ju_regex_Matcher, "java.util.regex.Matcher", ({
  et: 1,
  es: 1
}));
/** @constructor */
function $c_ju_regex_Pattern(_pattern, _flags, jsPattern, jsFlags, sticky, groupCount, groupNumberMap, namedGroups) {
  this.jq = null;
  this.md = null;
  this.me = false;
  this.jr = 0;
  this.mc = null;
  this.js = null;
  this.mf = null;
  this.jq = _pattern;
  this.md = jsFlags;
  this.me = sticky;
  this.jr = groupCount;
  this.mc = groupNumberMap;
  this.js = new RegExp(jsPattern, (this.md + (this.me ? "gy" : "g")));
  this.mf = new RegExp((("^(?:" + jsPattern) + ")$"), jsFlags);
}
$p = $c_ju_regex_Pattern.prototype = new $h_O();
$p.constructor = $c_ju_regex_Pattern;
/** @constructor */
function $h_ju_regex_Pattern() {
}
$h_ju_regex_Pattern.prototype = $p;
$p.qE = (function(input) {
  return this.mf.exec(input);
});
$p.rl = (function(input, start) {
  var regexp = this.js;
  regexp.lastIndex = start;
  return regexp.exec(input);
});
$p.rC = (function(group) {
  var length = this.jr;
  if (((group >>> 0) > (length >>> 0))) {
    $m_jl_BoundsChecks$().rg(group, length);
  }
  return (this.mc[group] | 0);
});
$p.t = (function() {
  return this.jq;
});
$p.rm = (function(inputStr, limit) {
  if ((inputStr === "")) {
    return new ($d_T.r().C)([""]);
  } else {
    var lim = ((limit > 0) ? limit : 2147483647);
    var matcher = new $c_ju_regex_Matcher(this, inputStr);
    var result = [];
    var prevEnd = 0;
    while ((((result.length | 0) < ((lim - 1) | 0)) && matcher.qK())) {
      if ((matcher.o9() !== 0)) {
        var beginIndex = prevEnd;
        var endIndex = matcher.oW();
        var $x_1 = result.push(inputStr.substring(beginIndex, endIndex));
      }
      prevEnd = matcher.o9();
    }
    var beginIndex$1 = prevEnd;
    result.push(inputStr.substring(beginIndex$1));
    var actualLength = (result.length | 0);
    if ((limit === 0)) {
      while (true) {
        if ((actualLength !== 0)) {
          var x = result[((actualLength - 1) | 0)];
          var $x_2 = ((x !== null) && $dp_equals__O__Z(x, ""));
        } else {
          var $x_2 = false;
        }
        if ($x_2) {
          actualLength = ((actualLength - 1) | 0);
        } else {
          break;
        }
      }
    }
    var r = new ($d_T.r().C)(actualLength);
    var end = actualLength;
    var i = 0;
    while ((i < end)) {
      var value = i;
      r.a[value] = result[value];
      i = ((1 + i) | 0);
    }
    return r;
  }
});
var $d_ju_regex_Pattern = new $TypeData().i($c_ju_regex_Pattern, "java.util.regex.Pattern", ({
  eu: 1,
  a: 1
}));
/** @constructor */
function $c_s_Console$() {
  this.mq = null;
  $n_s_Console$ = this;
  this.mq = new $c_s_util_DynamicVariable($m_jl_System$Streams$().lG);
}
$p = $c_s_Console$.prototype = new $h_O();
$p.constructor = $c_s_Console$;
/** @constructor */
function $h_s_Console$() {
}
$h_s_Console$.prototype = $p;
$p.rE = (function() {
  return this.mq.ki;
});
var $d_s_Console$ = new $TypeData().i($c_s_Console$, "scala.Console$", ({
  eF: 1,
  hD: 1
}));
var $n_s_Console$;
function $m_s_Console$() {
  if ((!$n_s_Console$)) {
    $n_s_Console$ = new $c_s_Console$();
  }
  return $n_s_Console$;
}
/** @constructor */
function $c_s_LowPriorityImplicits() {
}
$p = $c_s_LowPriorityImplicits.prototype = new $h_s_LowPriorityImplicits2();
$p.constructor = $c_s_LowPriorityImplicits;
/** @constructor */
function $h_s_LowPriorityImplicits() {
}
$h_s_LowPriorityImplicits.prototype = $p;
$p.sq = (function(xs) {
  if ((xs === null)) {
    return null;
  } else if ((xs.a.length === 0)) {
    var this$2 = $m_scm_ArraySeq$();
    $m_s_reflect_ManifestFactory$ObjectManifest$();
    return this$2.n7;
  } else {
    return new $c_scm_ArraySeq$ofRef(xs);
  }
});
$p.ld = (function(s) {
  return ((s === null) ? null : new $c_sci_WrappedString(s));
});
function $f_s_PartialFunction__applyOrElse__O__F1__O($thiz, x, default$1) {
  return ($thiz.cF(x) ? $thiz.d(x) : default$1.d(x));
}
/** @constructor */
function $c_sci_LazyList$Uninitialized$() {
}
$p = $c_sci_LazyList$Uninitialized$.prototype = new $h_O();
$p.constructor = $c_sci_LazyList$Uninitialized$;
/** @constructor */
function $h_sci_LazyList$Uninitialized$() {
}
$h_sci_LazyList$Uninitialized$.prototype = $p;
var $d_sci_LazyList$Uninitialized$ = new $TypeData().i($c_sci_LazyList$Uninitialized$, "scala.collection.immutable.LazyList$Uninitialized$", ({
  fT: 1,
  a: 1
}));
var $n_sci_LazyList$Uninitialized$;
function $m_sci_LazyList$Uninitialized$() {
  if ((!$n_sci_LazyList$Uninitialized$)) {
    $n_sci_LazyList$Uninitialized$ = new $c_sci_LazyList$Uninitialized$();
  }
  return $n_sci_LazyList$Uninitialized$;
}
/** @constructor */
function $c_sci_List$$anon$1() {
}
$p = $c_sci_List$$anon$1.prototype = new $h_O();
$p.constructor = $c_sci_List$$anon$1;
/** @constructor */
function $h_sci_List$$anon$1() {
}
$h_sci_List$$anon$1.prototype = $p;
$p.t = (function() {
  return "<function1>";
});
$p.d = (function(x) {
  return this;
});
var $d_sci_List$$anon$1 = new $TypeData().i($c_sci_List$$anon$1, "scala.collection.immutable.List$$anon$1", ({
  fV: 1,
  i: 1
}));
/** @constructor */
function $c_sci_MapNode() {
}
$p = $c_sci_MapNode.prototype = new $h_sci_Node();
$p.constructor = $c_sci_MapNode;
/** @constructor */
function $h_sci_MapNode() {
}
$h_sci_MapNode.prototype = $p;
/** @constructor */
function $c_sci_SetNode() {
}
$p = $c_sci_SetNode.prototype = new $h_sci_Node();
$p.constructor = $c_sci_SetNode;
/** @constructor */
function $h_sci_SetNode() {
}
$h_sci_SetNode.prototype = $p;
function $f_scm_Growable__addAll__sc_IterableOnce__scm_Growable($thiz, elems) {
  if ((elems === $thiz)) {
    $thiz.b7($m_scm_Buffer$().fD(elems));
  } else {
    var it = elems.c();
    while (it.k()) {
      $thiz.aq(it.f());
    }
  }
  return $thiz;
}
function $f_s_concurrent_BatchingExecutor__submitSyncBatched__jl_Runnable__V($thiz, runnable) {
  var tl = $thiz.nj;
  var b = tl.S();
  if ((b instanceof $c_s_concurrent_BatchingExecutor$SyncBatch)) {
    b.rI(runnable);
  } else {
    var i = ((b !== null) ? b : 0);
    if ((i < 16)) {
      tl.hU(((1 + i) | 0));
      try {
        runnable.fJ();
      } catch (e) {
        var e$2 = ((e instanceof $c_jl_Throwable) ? e : new $c_sjs_js_JavaScriptException(e));
        if (false) {
          $m_s_concurrent_ExecutionContext$().gl.d(e$2);
        } else {
          matchResult3: {
            if ($m_s_util_control_NonFatal$().iR(e$2)) {
              $m_s_concurrent_ExecutionContext$().gl.d(e$2);
              break matchResult3;
            }
            throw ((e$2 instanceof $c_sjs_js_JavaScriptException) ? e$2.di : e$2);
          }
        }
      } finally {
        tl.hU(b);
      }
    } else {
      var batch = new $c_s_concurrent_BatchingExecutor$SyncBatch($thiz, runnable);
      tl.hU(batch);
      batch.fJ();
      tl.hU(b);
    }
  }
}
/** @constructor */
function $c_s_concurrent_duration_package$DurationInt(n) {
  this.hj = 0;
  this.hj = n;
}
$p = $c_s_concurrent_duration_package$DurationInt.prototype = new $h_O();
$p.constructor = $c_s_concurrent_duration_package$DurationInt;
/** @constructor */
function $h_s_concurrent_duration_package$DurationInt() {
}
$h_s_concurrent_duration_package$DurationInt.prototype = $p;
$p.u = (function() {
  return this.hj;
});
$p.p = (function(x$0) {
  return $m_s_concurrent_duration_package$DurationInt$().qz(this.hj, x$0);
});
function $isArrayOf_s_concurrent_duration_package$DurationInt(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cP)));
}
var $d_s_concurrent_duration_package$DurationInt = new $TypeData().i($c_s_concurrent_duration_package$DurationInt, "scala.concurrent.duration.package$DurationInt", ({
  cP: 1,
  hz: 1
}));
/** @constructor */
function $c_s_concurrent_impl_Promise$ManyCallbacks(first, rest) {
  this.kd = null;
  this.ke = null;
  this.kd = first;
  this.ke = rest;
}
$p = $c_s_concurrent_impl_Promise$ManyCallbacks.prototype = new $h_O();
$p.constructor = $c_s_concurrent_impl_Promise$ManyCallbacks;
/** @constructor */
function $h_s_concurrent_impl_Promise$ManyCallbacks() {
}
$h_s_concurrent_impl_Promise$ManyCallbacks.prototype = $p;
$p.t = (function() {
  return "ManyCallbacks";
});
function $isArrayOf_s_concurrent_impl_Promise$ManyCallbacks(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cR)));
}
var $d_s_concurrent_impl_Promise$ManyCallbacks = new $TypeData().i($c_s_concurrent_impl_Promise$ManyCallbacks, "scala.concurrent.impl.Promise$ManyCallbacks", ({
  cR: 1,
  aJ: 1
}));
/** @constructor */
function $c_s_math_Ordering$() {
}
$p = $c_s_math_Ordering$.prototype = new $h_O();
$p.constructor = $c_s_math_Ordering$;
/** @constructor */
function $h_s_math_Ordering$() {
}
$h_s_math_Ordering$.prototype = $p;
var $d_s_math_Ordering$ = new $TypeData().i($c_s_math_Ordering$, "scala.math.Ordering$", ({
  hF: 1,
  hE: 1
}));
var $n_s_math_Ordering$;
function $m_s_math_Ordering$() {
  if ((!$n_s_math_Ordering$)) {
    $n_s_math_Ordering$ = new $c_s_math_Ordering$();
  }
  return $n_s_math_Ordering$;
}
/** @constructor */
function $c_sr_AbstractFunction0() {
}
$p = $c_sr_AbstractFunction0.prototype = new $h_O();
$p.constructor = $c_sr_AbstractFunction0;
/** @constructor */
function $h_sr_AbstractFunction0() {
}
$h_sr_AbstractFunction0.prototype = $p;
$p.t = (function() {
  return "<function0>";
});
/** @constructor */
function $c_sr_AbstractFunction1() {
}
$p = $c_sr_AbstractFunction1.prototype = new $h_O();
$p.constructor = $c_sr_AbstractFunction1;
/** @constructor */
function $h_sr_AbstractFunction1() {
}
$h_sr_AbstractFunction1.prototype = $p;
$p.t = (function() {
  return "<function1>";
});
/** @constructor */
function $c_sr_AbstractFunction2() {
}
$p = $c_sr_AbstractFunction2.prototype = new $h_O();
$p.constructor = $c_sr_AbstractFunction2;
/** @constructor */
function $h_sr_AbstractFunction2() {
}
$h_sr_AbstractFunction2.prototype = $p;
$p.t = (function() {
  return "<function2>";
});
/** @constructor */
function $c_sr_AbstractFunction3() {
}
$p = $c_sr_AbstractFunction3.prototype = new $h_O();
$p.constructor = $c_sr_AbstractFunction3;
/** @constructor */
function $h_sr_AbstractFunction3() {
}
$h_sr_AbstractFunction3.prototype = $p;
$p.t = (function() {
  return "<function3>";
});
/** @constructor */
function $c_sr_IntRef(elem) {
  this.iD = 0;
  this.iD = elem;
}
$p = $c_sr_IntRef.prototype = new $h_O();
$p.constructor = $c_sr_IntRef;
/** @constructor */
function $h_sr_IntRef() {
}
$h_sr_IntRef.prototype = $p;
$p.t = (function() {
  return ("" + this.iD);
});
var $d_sr_IntRef = new $TypeData().i($c_sr_IntRef, "scala.runtime.IntRef", ({
  iw: 1,
  a: 1
}));
/** @constructor */
function $c_sr_LazyRef() {
  this.iE = false;
  this.iF = null;
}
$p = $c_sr_LazyRef.prototype = new $h_O();
$p.constructor = $c_sr_LazyRef;
/** @constructor */
function $h_sr_LazyRef() {
}
$h_sr_LazyRef.prototype = $p;
$p.rd = (function(value) {
  this.iF = value;
  this.iE = true;
  return value;
});
$p.t = (function() {
  return ("LazyRef " + (this.iE ? ("of: " + this.iF) : "thunk"));
});
var $d_sr_LazyRef = new $TypeData().i($c_sr_LazyRef, "scala.runtime.LazyRef", ({
  iy: 1,
  a: 1
}));
/** @constructor */
function $c_sr_ObjectRef(elem) {
  this.iG = null;
  this.iG = elem;
}
$p = $c_sr_ObjectRef.prototype = new $h_O();
$p.constructor = $c_sr_ObjectRef;
/** @constructor */
function $h_sr_ObjectRef() {
}
$h_sr_ObjectRef.prototype = $p;
$p.t = (function() {
  return ("" + this.iG);
});
var $d_sr_ObjectRef = new $TypeData().i($c_sr_ObjectRef, "scala.runtime.ObjectRef", ({
  iC: 1,
  a: 1
}));
/** @constructor */
function $c_s_util_hashing_MurmurHash3$() {
  this.aY = 0;
  this.el = 0;
  this.iI = 0;
  this.iH = 0;
  $n_s_util_hashing_MurmurHash3$ = this;
  this.aY = $f_T__hashCode__I("Seq");
  this.el = $f_T__hashCode__I("Map");
  this.iI = $f_T__hashCode__I("Set");
  this.iH = this.gA($m_sci_Nil$(), this.el);
}
$p = $c_s_util_hashing_MurmurHash3$.prototype = new $h_s_util_hashing_MurmurHash3();
$p.constructor = $c_s_util_hashing_MurmurHash3$;
/** @constructor */
function $h_s_util_hashing_MurmurHash3$() {
}
$h_s_util_hashing_MurmurHash3$.prototype = $p;
$p.d7 = (function(x, y) {
  return this.p2($m_sr_Statics$().y(x), $m_sr_Statics$().y(y), (-889275714));
});
$p.l5 = (function(xs) {
  return ($is_sc_IndexedSeq(xs) ? this.rb(xs, this.aY) : ((xs instanceof $c_sci_List) ? this.ro(xs, this.aY) : this.rD(xs, this.aY)));
});
$p.ru = (function(xs) {
  if (xs.g()) {
    return this.iH;
  } else {
    var accum = new $c_s_util_hashing_MurmurHash3$accum$1();
    var h = this.el;
    xs.dm(accum);
    h = this.x(h, accum.iJ);
    h = this.x(h, accum.iK);
    h = this.eq(h, accum.iL);
    return this.bd(h, accum.iM);
  }
});
var $d_s_util_hashing_MurmurHash3$ = new $TypeData().i($c_s_util_hashing_MurmurHash3$, "scala.util.hashing.MurmurHash3$", ({
  jg: 1,
  jf: 1
}));
var $n_s_util_hashing_MurmurHash3$;
function $m_s_util_hashing_MurmurHash3$() {
  if ((!$n_s_util_hashing_MurmurHash3$)) {
    $n_s_util_hashing_MurmurHash3$ = new $c_s_util_hashing_MurmurHash3$();
  }
  return $n_s_util_hashing_MurmurHash3$;
}
/** @constructor */
function $c_s_util_hashing_MurmurHash3$accum$1() {
  this.iJ = 0;
  this.iK = 0;
  this.iM = 0;
  this.iL = 0;
  this.iJ = 0;
  this.iK = 0;
  this.iM = 0;
  this.iL = 1;
}
$p = $c_s_util_hashing_MurmurHash3$accum$1.prototype = new $h_O();
$p.constructor = $c_s_util_hashing_MurmurHash3$accum$1;
/** @constructor */
function $h_s_util_hashing_MurmurHash3$accum$1() {
}
$h_s_util_hashing_MurmurHash3$accum$1.prototype = $p;
$p.t = (function() {
  return "<function2>";
});
$p.q1 = (function(k, v) {
  var h = $m_s_util_hashing_MurmurHash3$().d7(k, v);
  this.iJ = ((this.iJ + h) | 0);
  this.iK = (this.iK ^ h);
  this.iL = Math.imul(this.iL, (1 | h));
  this.iM = ((1 + this.iM) | 0);
});
$p.ca = (function(v1, v2) {
  this.q1(v1, v2);
});
var $d_s_util_hashing_MurmurHash3$accum$1 = new $TypeData().i($c_s_util_hashing_MurmurHash3$accum$1, "scala.util.hashing.MurmurHash3$accum$1", ({
  jh: 1,
  aV: 1
}));
function $ct_s_util_matching_Regex__ju_regex_Pattern__sci_Seq__($thiz, pattern, groupNames) {
  $thiz.kj = pattern;
  return $thiz;
}
function $ct_s_util_matching_Regex__T__sci_Seq__($thiz, regex, groupNames) {
  $ct_s_util_matching_Regex__ju_regex_Pattern__sci_Seq__($thiz, $m_ju_regex_PatternCompiler$().nX(regex, 0), groupNames);
  return $thiz;
}
/** @constructor */
function $c_s_util_matching_Regex() {
  this.kj = null;
}
$p = $c_s_util_matching_Regex.prototype = new $h_O();
$p.constructor = $c_s_util_matching_Regex;
/** @constructor */
function $h_s_util_matching_Regex() {
}
$h_s_util_matching_Regex.prototype = $p;
$p.jc = (function(s) {
  var m = new $c_ju_regex_Matcher(this.kj, $dp_toString__T(s));
  if (m.rv()) {
    $m_sci_List$();
    var n = m.gK.jr;
    var b = new $c_scm_ListBuffer();
    var i = 0;
    while ((i < n)) {
      b.gm(m.r4(((1 + i) | 0)));
      i = ((1 + i) | 0);
    }
    return new $c_s_Some(b.c2());
  } else {
    return $m_s_None$();
  }
});
$p.t = (function() {
  return this.kj.jq;
});
var $d_s_util_matching_Regex = new $TypeData().i($c_s_util_matching_Regex, "scala.util.matching.Regex", ({
  ji: 1,
  a: 1
}));
/** @constructor */
function $c_Ldotty_tools_scaladoc_PageEntry$() {
}
$p = $c_Ldotty_tools_scaladoc_PageEntry$.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_PageEntry$;
/** @constructor */
function $h_Ldotty_tools_scaladoc_PageEntry$() {
}
$h_Ldotty_tools_scaladoc_PageEntry$.prototype = $p;
$p.q0 = (function(jsObj) {
  var $x_6 = jsObj.t;
  var $x_5 = jsObj.d;
  var $x_4 = jsObj.x;
  var $x_3 = jsObj.i;
  var $x_2 = jsObj.l;
  var $x_1 = jsObj.e;
  var this$1 = jsObj.n;
  return new $c_Ldotty_tools_scaladoc_PageEntry($x_6, $x_5, $x_4, $x_3, $x_2, (!(!$x_1)), this$1.toLowerCase(), jsObj.k, $m_Ldotty_tools_scaladoc_StringUtils$().kw(jsObj.n));
});
var $d_Ldotty_tools_scaladoc_PageEntry$ = new $TypeData().i($c_Ldotty_tools_scaladoc_PageEntry$, "dotty.tools.scaladoc.PageEntry$", ({
  d8: 1,
  cT: 1,
  cU: 1
}));
var $n_Ldotty_tools_scaladoc_PageEntry$;
function $m_Ldotty_tools_scaladoc_PageEntry$() {
  if ((!$n_Ldotty_tools_scaladoc_PageEntry$)) {
    $n_Ldotty_tools_scaladoc_PageEntry$ = new $c_Ldotty_tools_scaladoc_PageEntry$();
  }
  return $n_Ldotty_tools_scaladoc_PageEntry$;
}
var $b_Ldotty_tools_scaladoc_RecentQuery;
function $a_Ldotty_tools_scaladoc_RecentQuery() {
  if ((!$b_Ldotty_tools_scaladoc_RecentQuery)) {
    $b_Ldotty_tools_scaladoc_RecentQuery = class $b_Ldotty_tools_scaladoc_RecentQuery extends Object {
      constructor(arg, arg$2) {
        var query = null;
        var timestamp = 0.0;
        query = arg;
        timestamp = (+arg$2);
        super();
        Object.defineProperty(this, "query", ({
          "configurable": true,
          "enumerable": true,
          "writable": true,
          "value": null
        }));
        Object.defineProperty(this, "timestamp", ({
          "configurable": true,
          "enumerable": true,
          "writable": true,
          "value": 0.0
        }));
        this.query = query;
        this.timestamp = timestamp;
      }
    };
  }
  return $b_Ldotty_tools_scaladoc_RecentQuery;
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_utils_HTML$Tag$() {
}
$p = $c_Ldotty_tools_scaladoc_utils_HTML$Tag$.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_utils_HTML$Tag$;
/** @constructor */
function $h_Ldotty_tools_scaladoc_utils_HTML$Tag$() {
}
$h_Ldotty_tools_scaladoc_utils_HTML$Tag$.prototype = $p;
$p.E = (function(s) {
  return new $c_Ldotty_tools_scaladoc_utils_HTML$Tag(new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => document.createElement(s))));
});
var $d_Ldotty_tools_scaladoc_utils_HTML$Tag$ = new $TypeData().i($c_Ldotty_tools_scaladoc_utils_HTML$Tag$, "dotty.tools.scaladoc.utils.HTML$Tag$", ({
  di: 1,
  cT: 1,
  cU: 1
}));
var $n_Ldotty_tools_scaladoc_utils_HTML$Tag$;
function $m_Ldotty_tools_scaladoc_utils_HTML$Tag$() {
  if ((!$n_Ldotty_tools_scaladoc_utils_HTML$Tag$)) {
    $n_Ldotty_tools_scaladoc_utils_HTML$Tag$ = new $c_Ldotty_tools_scaladoc_utils_HTML$Tag$();
  }
  return $n_Ldotty_tools_scaladoc_utils_HTML$Tag$;
}
/** @constructor */
function $c_jl_Class($data) {
  this.J = $data;
}
$p = $c_jl_Class.prototype = new $h_O();
$p.constructor = $c_jl_Class;
/** @constructor */
function $h_jl_Class() {
}
$h_jl_Class.prototype = $p;
$p.t = (function() {
  return ((this.J.Y ? "interface " : (this.J.X ? "" : "class ")) + this.J.N);
});
var $d_jl_Class = new $TypeData().i($c_jl_Class, "java.lang.Class", ({
  ds: 1,
  a: 1,
  a5: 1
}));
function $ct_jl_Enum__T__I__($thiz, _name, _ordinal) {
  $thiz.dx = _name;
  $thiz.d8 = _ordinal;
  return $thiz;
}
/** @constructor */
function $c_jl_Enum() {
  this.dx = null;
  this.d8 = 0;
}
$p = $c_jl_Enum.prototype = new $h_O();
$p.constructor = $c_jl_Enum;
/** @constructor */
function $h_jl_Enum() {
}
$h_jl_Enum.prototype = $p;
$p.t = (function() {
  return this.dx;
});
$p.p = (function(that) {
  return (this === that);
});
$p.u = (function() {
  return $systemIdentityHashCode(this);
});
$p.qc = (function(o) {
  var x = this.d8;
  var y = o.d8;
  return ((x === y) ? 0 : ((x < y) ? (-1) : 1));
});
$p.ks = (function(o) {
  return this.qc(o);
});
class $c_jl_Error extends $c_jl_Throwable {
}
function $isArrayOf_jl_Error(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.aR)));
}
class $c_jl_Exception extends $c_jl_Throwable {
}
/** @constructor */
function $c_s_Predef$() {
  this.pg = null;
  this.ph = null;
  $n_s_Predef$ = this;
  $m_sci_List$();
  this.pg = $m_sci_Map$();
  this.ph = $m_sci_Set$();
}
$p = $c_s_Predef$.prototype = new $h_s_LowPriorityImplicits();
$p.constructor = $c_s_Predef$;
/** @constructor */
function $h_s_Predef$() {
}
$h_s_Predef$.prototype = $p;
$p.oG = (function(requirement) {
  if ((!requirement)) {
    throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), "requirement failed");
  }
});
var $d_s_Predef$ = new $TypeData().i($c_s_Predef$, "scala.Predef$", ({
  eS: 1,
  eI: 1,
  eJ: 1
}));
var $n_s_Predef$;
function $m_s_Predef$() {
  if ((!$n_s_Predef$)) {
    $n_s_Predef$ = new $c_s_Predef$();
  }
  return $n_s_Predef$;
}
function $f_s_Product2__productElement__I__O($thiz, n) {
  switch (n) {
    case 0: {
      return $thiz.aw();
      break;
    }
    case 1: {
      return $thiz.ap();
      break;
    }
    default: {
      throw $ct_jl_IndexOutOfBoundsException__T__(new $c_jl_IndexOutOfBoundsException(), (n + " is out of bounds (min 0, max 1)"));
    }
  }
}
function $f_s_Product3__productElement__I__O($thiz, n) {
  switch (n) {
    case 0: {
      return $thiz.g5;
      break;
    }
    case 1: {
      return $thiz.g6;
      break;
    }
    case 2: {
      return $thiz.g7;
      break;
    }
    default: {
      throw $ct_jl_IndexOutOfBoundsException__T__(new $c_jl_IndexOutOfBoundsException(), (n + " is out of bounds (min 0, max 2)"));
    }
  }
}
function $ct_sc_ClassTagIterableFactory$AnyIterableDelegate__sc_ClassTagIterableFactory__($thiz, delegate) {
  $thiz.gS = delegate;
  return $thiz;
}
/** @constructor */
function $c_sc_ClassTagIterableFactory$AnyIterableDelegate() {
  this.gS = null;
}
$p = $c_sc_ClassTagIterableFactory$AnyIterableDelegate.prototype = new $h_O();
$p.constructor = $c_sc_ClassTagIterableFactory$AnyIterableDelegate;
/** @constructor */
function $h_sc_ClassTagIterableFactory$AnyIterableDelegate() {
}
$h_sc_ClassTagIterableFactory$AnyIterableDelegate.prototype = $p;
$p.a3 = (function(it) {
  return this.gS.kH(it, $m_s_reflect_ManifestFactory$AnyManifest$());
});
$p.T = (function() {
  return this.gS.j2($m_s_reflect_ManifestFactory$AnyManifest$());
});
$p.em = (function(elems) {
  return this.gS.kH(elems, $m_s_reflect_ManifestFactory$AnyManifest$());
});
function $ct_sc_IterableFactory$Delegate__sc_IterableFactory__($thiz, delegate) {
  $thiz.gT = delegate;
  return $thiz;
}
/** @constructor */
function $c_sc_IterableFactory$Delegate() {
  this.gT = null;
}
$p = $c_sc_IterableFactory$Delegate.prototype = new $h_O();
$p.constructor = $c_sc_IterableFactory$Delegate;
/** @constructor */
function $h_sc_IterableFactory$Delegate() {
}
$h_sc_IterableFactory$Delegate.prototype = $p;
$p.a3 = (function(it) {
  return this.gT.a3(it);
});
$p.T = (function() {
  return this.gT.T();
});
/** @constructor */
function $c_sc_IterableOnceOps$Maximized(descriptor, f, cmp) {
  this.jH = null;
  this.mz = null;
  this.ie = null;
  this.ig = null;
  this.ih = false;
  this.jH = f;
  this.mz = cmp;
  this.ie = null;
  this.ig = null;
  this.ih = false;
}
$p = $c_sc_IterableOnceOps$Maximized.prototype = new $h_sr_AbstractFunction2();
$p.constructor = $c_sc_IterableOnceOps$Maximized;
/** @constructor */
function $h_sc_IterableOnceOps$Maximized() {
}
$h_sc_IterableOnceOps$Maximized.prototype = $p;
$p.sl = (function() {
  return (this.ih ? new $c_s_Some(this.ie) : $m_s_None$());
});
$p.nS = (function(m, a) {
  if (m.ih) {
    var fa = this.jH.d(a);
    if ((!(!this.mz.ca(fa, this.ig)))) {
      this.ig = fa;
      this.ie = a;
    }
    return m;
  } else {
    m.ih = true;
    m.ie = a;
    m.ig = this.jH.d(a);
    return m;
  }
});
$p.ca = (function(v1, v2) {
  return this.nS(v1, v2);
});
var $d_sc_IterableOnceOps$Maximized = new $TypeData().i($c_sc_IterableOnceOps$Maximized, "scala.collection.IterableOnceOps$Maximized", ({
  f8: 1,
  cX: 1,
  aV: 1
}));
function $f_sc_IterableOps__sizeCompare__I__I($thiz, otherSize) {
  if ((otherSize < 0)) {
    return 1;
  } else {
    var known = $thiz.q();
    if ((known >= 0)) {
      return ((known === otherSize) ? 0 : ((known < otherSize) ? (-1) : 1));
    } else {
      var i = 0;
      var it = $thiz.c();
      while (it.k()) {
        if ((i === otherSize)) {
          return 1;
        }
        it.f();
        i = ((1 + i) | 0);
      }
      return ((i - otherSize) | 0);
    }
  }
}
function $f_sc_IterableOps__groupBy__F1__sci_Map($thiz, f) {
  var m = $m_scm_Map$().hF();
  var it = $thiz.c();
  while (it.k()) {
    var elem = it.f();
    var key = f.d(elem);
    m.r3(key, new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => $thiz.cI()))).aq(elem);
  }
  var result = $m_sci_HashMap$().h4;
  var mapIt = m.c();
  while (mapIt.k()) {
    var x1 = mapIt.f();
    x1.aw();
    x1.ap();
    var \u03b42$ = x1;
    var k$2 = \u03b42$.aw();
    var v$2 = \u03b42$.ap();
    result = result.fM(k$2, v$2.aU());
  }
  return result;
}
function $f_sc_IterableOps__map__F1__O($thiz, f) {
  return $thiz.aH().a3($ct_sc_View$Map__sc_IterableOps__F1__(new $c_sc_View$Map(), $thiz, f));
}
function $f_sc_IterableOps__collect__s_PartialFunction__O($thiz, pf) {
  return $thiz.aH().a3(new $c_sc_View$Collect($thiz, pf));
}
function $f_sc_IterableOps__zipWithIndex__O($thiz) {
  return $thiz.aH().a3(new $c_sc_View$ZipWithIndex($thiz));
}
function $f_sc_Iterator__indexWhere__F1__I__I($thiz, p, from) {
  var i = ((from > 0) ? from : 0);
  var dropped = $thiz.cE(from);
  while (dropped.k()) {
    if ((!(!p.d(dropped.f())))) {
      return i;
    }
    i = ((1 + i) | 0);
  }
  return (-1);
}
function $f_sc_Iterator__concat__F0__sc_Iterator($thiz, xs) {
  return new $c_sc_Iterator$ConcatIterator($thiz).fB(xs);
}
function $f_sc_Iterator__sliceIterator__I__I__sc_Iterator($thiz, from, until) {
  var lo = ((from > 0) ? from : 0);
  var rest = ((until < 0) ? (-1) : ((until <= lo) ? 0 : ((until - lo) | 0)));
  return ((rest === 0) ? $m_sc_Iterator$().G : new $c_sc_Iterator$SliceIterator($thiz, lo, rest));
}
function $f_sc_Iterator__sameElements__sc_IterableOnce__Z($thiz, that) {
  var those = that.c();
  while ($thiz.k()) {
    if ((!those.k())) {
      return false;
    }
    if ((!$m_sr_BoxesRunTime$().n($thiz.f(), those.f()))) {
      return false;
    }
  }
  return (!those.k());
}
/** @constructor */
function $c_sc_Iterator$() {
  this.G = null;
  $n_sc_Iterator$ = this;
  this.G = new $c_sc_Iterator$$anon$19();
}
$p = $c_sc_Iterator$.prototype = new $h_O();
$p.constructor = $c_sc_Iterator$;
/** @constructor */
function $h_sc_Iterator$() {
}
$h_sc_Iterator$.prototype = $p;
$p.T = (function() {
  return new $c_sc_Iterator$$anon$21();
});
$p.a3 = (function(source) {
  return source.c();
});
var $d_sc_Iterator$ = new $TypeData().i($c_sc_Iterator$, "scala.collection.Iterator$", ({
  f9: 1,
  a: 1,
  B: 1
}));
var $n_sc_Iterator$;
function $m_sc_Iterator$() {
  if ((!$n_sc_Iterator$)) {
    $n_sc_Iterator$ = new $c_sc_Iterator$();
  }
  return $n_sc_Iterator$;
}
function $ct_sc_MapFactory$Delegate__sc_MapFactory__($thiz, delegate) {
  $thiz.g9 = delegate;
  return $thiz;
}
/** @constructor */
function $c_sc_MapFactory$Delegate() {
  this.g9 = null;
}
$p = $c_sc_MapFactory$Delegate.prototype = new $h_O();
$p.constructor = $c_sc_MapFactory$Delegate;
/** @constructor */
function $h_sc_MapFactory$Delegate() {
}
$h_sc_MapFactory$Delegate.prototype = $p;
$p.a3 = (function(it) {
  return this.g9.a3(it);
});
$p.hF = (function() {
  return this.g9.hF();
});
$p.T = (function() {
  return this.g9.T();
});
/** @constructor */
function $c_sc_View$() {
}
$p = $c_sc_View$.prototype = new $h_O();
$p.constructor = $c_sc_View$;
/** @constructor */
function $h_sc_View$() {
}
$h_sc_View$.prototype = $p;
$p.iT = (function(it) {
  return ($is_sc_View(it) ? it : ($is_sc_Iterable(it) ? new $c_sc_View$$anon$1(new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c(((it$2) => (() => it$2.c()))(it))) : $ct_sc_SeqView$Id__sc_SeqOps__(new $c_sc_SeqView$Id(), $m_sci_LazyList$().kJ(it))));
});
$p.T = (function() {
  return new $c_scm_Builder$$anon$1(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((it$2) => this.iT(it$2))), ($m_scm_ArrayBuffer$(), new $c_scm_ArrayBuffer$$anon$1()));
});
$p.a3 = (function(source) {
  return this.iT(source);
});
var $d_sc_View$ = new $TypeData().i($c_sc_View$, "scala.collection.View$", ({
  fu: 1,
  a: 1,
  B: 1
}));
var $n_sc_View$;
function $m_sc_View$() {
  if ((!$n_sc_View$)) {
    $n_sc_View$ = new $c_sc_View$();
  }
  return $n_sc_View$;
}
/** @constructor */
function $c_sci_BitmapIndexedMapNode(dataMap, nodeMap, content, originalHashes, size, cachedJavaKeySetHashCode) {
  this.ac = 0;
  this.az = 0;
  this.b1 = null;
  this.ch = null;
  this.bs = 0;
  this.c3 = 0;
  this.ac = dataMap;
  this.az = nodeMap;
  this.b1 = content;
  this.ch = originalHashes;
  this.bs = size;
  this.c3 = cachedJavaKeySetHashCode;
}
$p = $c_sci_BitmapIndexedMapNode.prototype = new $h_sci_MapNode();
$p.constructor = $c_sci_BitmapIndexedMapNode;
/** @constructor */
function $h_sci_BitmapIndexedMapNode() {
}
$h_sci_BitmapIndexedMapNode.prototype = $p;
$p.M = (function() {
  return this.bs;
});
$p.bS = (function() {
  return this.c3;
});
$p.dP = (function(index) {
  return this.b1.a[(index << 1)];
});
$p.dp = (function(index) {
  return this.b1.a[((1 + (index << 1)) | 0)];
});
$p.op = (function(index) {
  return $ct_T2__O__O__(new $c_T2(), this.b1.a[(index << 1)], this.b1.a[((1 + (index << 1)) | 0)]);
});
$p.co = (function(index) {
  return this.ch.a[index];
});
$p.dn = (function(index) {
  return this.b1.a[((((this.b1.a.length - 1) | 0) - index) | 0)];
});
$p.ko = (function(key, originalHash, keyHash, shift) {
  var mask = $m_sci_Node$().cH(keyHash, shift);
  var bitpos = $m_sci_Node$().bZ(mask);
  if (((this.ac & bitpos) !== 0)) {
    var index = $m_sci_Node$().bL(this.ac, mask, bitpos);
    if ($m_sr_BoxesRunTime$().n(key, this.dP(index))) {
      return this.dp(index);
    } else {
      throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), ("key not found: " + key));
    }
  } else if (((this.az & bitpos) !== 0)) {
    return this.dn($m_sci_Node$().bL(this.az, mask, bitpos)).ko(key, originalHash, keyHash, ((5 + shift) | 0));
  } else {
    throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), ("key not found: " + key));
  }
});
$p.iV = (function(key, originalHash, keyHash, shift) {
  var mask = $m_sci_Node$().cH(keyHash, shift);
  var bitpos = $m_sci_Node$().bZ(mask);
  if (((this.ac & bitpos) !== 0)) {
    var index = $m_sci_Node$().bL(this.ac, mask, bitpos);
    return ($m_sr_BoxesRunTime$().n(key, this.dP(index)) ? new $c_s_Some(this.dp(index)) : $m_s_None$());
  } else {
    return (((this.az & bitpos) !== 0) ? this.dn($m_sci_Node$().bL(this.az, mask, bitpos)).iV(key, originalHash, keyHash, ((5 + shift) | 0)) : $m_s_None$());
  }
});
$p.kN = (function(key, originalHash, keyHash, shift, f) {
  var mask = $m_sci_Node$().cH(keyHash, shift);
  var bitpos = $m_sci_Node$().bZ(mask);
  if (((this.ac & bitpos) !== 0)) {
    var index = $m_sci_Node$().bL(this.ac, mask, bitpos);
    return ($m_sr_BoxesRunTime$().n(key, this.dP(index)) ? this.dp(index) : f.a2());
  } else {
    return (((this.az & bitpos) !== 0) ? this.dn($m_sci_Node$().bL(this.az, mask, bitpos)).kN(key, originalHash, keyHash, ((5 + shift) | 0), f) : f.a2());
  }
});
$p.ku = (function(key, originalHash, keyHash, shift) {
  var mask = $m_sci_Node$().cH(keyHash, shift);
  var bitpos = $m_sci_Node$().bZ(mask);
  if (((this.ac & bitpos) !== 0)) {
    var index = $m_sci_Node$().bL(this.ac, mask, bitpos);
    return ((this.ch.a[index] === originalHash) && $m_sr_BoxesRunTime$().n(key, this.dP(index)));
  } else {
    return (((this.az & bitpos) !== 0) && this.dn($m_sci_Node$().bL(this.az, mask, bitpos)).ku(key, originalHash, keyHash, ((5 + shift) | 0)));
  }
});
$p.p5 = (function(key, value, originalHash, keyHash, shift, replaceValue) {
  var mask = $m_sci_Node$().cH(keyHash, shift);
  var bitpos = $m_sci_Node$().bZ(mask);
  if (((this.ac & bitpos) !== 0)) {
    var index = $m_sci_Node$().bL(this.ac, mask, bitpos);
    var key0 = this.dP(index);
    var key0UnimprovedHash = this.co(index);
    if (((key0UnimprovedHash === originalHash) && $m_sr_BoxesRunTime$().n(key0, key))) {
      if (replaceValue) {
        var value0 = this.dp(index);
        return ((Object.is(key0, key) && Object.is(value0, value)) ? this : this.qk(bitpos, key, value));
      } else {
        return this;
      }
    } else {
      var value0$2 = this.dp(index);
      var key0Hash = $m_sc_Hashing$().bD(key0UnimprovedHash);
      return this.qg(bitpos, key0Hash, this.l1(key0, value0$2, key0UnimprovedHash, key0Hash, key, value, originalHash, keyHash, ((5 + shift) | 0)));
    }
  } else if (((this.az & bitpos) !== 0)) {
    var index$2 = $m_sci_Node$().bL(this.az, mask, bitpos);
    var subNode = this.dn(index$2);
    var subNodeNew$2 = subNode.p6(key, value, originalHash, keyHash, ((5 + shift) | 0), replaceValue);
    return ((subNodeNew$2 === subNode) ? this : this.qi(bitpos, subNode, subNodeNew$2));
  } else {
    return this.qe(bitpos, key, originalHash, keyHash, value);
  }
});
$p.l1 = (function(key0, value0, originalHash0, keyHash0, key1, value1, originalHash1, keyHash1, shift) {
  if ((shift >= 32)) {
    return new $c_sci_HashCollisionMapNode(originalHash0, keyHash0, $m_sci_Vector$().iU(new $c_sjsr_WrappedVarArgs([$ct_T2__O__O__(new $c_T2(), key0, value0), $ct_T2__O__O__(new $c_T2(), key1, value1)])));
  } else {
    var mask0 = $m_sci_Node$().cH(keyHash0, shift);
    var mask1 = $m_sci_Node$().cH(keyHash1, shift);
    var newCachedHash = ((keyHash0 + keyHash1) | 0);
    if ((mask0 !== mask1)) {
      var dataMap = ($m_sci_Node$().bZ(mask0) | $m_sci_Node$().bZ(mask1));
      if ((mask0 < mask1)) {
        var xs = new $c_sjsr_WrappedVarArgs([key0, value0, key1, value1]);
        var array$2 = new $ac_O(xs.l());
        var iterator = $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), new $c_sc_IndexedSeqView$Id(xs));
        var i = 0;
        while ((iterator.br > 0)) {
          array$2.a[i] = iterator.f();
          i = ((1 + i) | 0);
        }
        return new $c_sci_BitmapIndexedMapNode(dataMap, 0, array$2, new $ac_I(new Int32Array([originalHash0, originalHash1])), 2, newCachedHash);
      } else {
        var xs$1 = new $c_sjsr_WrappedVarArgs([key1, value1, key0, value0]);
        var array$4 = new $ac_O(xs$1.l());
        var iterator$1 = $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), new $c_sc_IndexedSeqView$Id(xs$1));
        var i$1 = 0;
        while ((iterator$1.br > 0)) {
          array$4.a[i$1] = iterator$1.f();
          i$1 = ((1 + i$1) | 0);
        }
        return new $c_sci_BitmapIndexedMapNode(dataMap, 0, array$4, new $ac_I(new Int32Array([originalHash1, originalHash0])), 2, newCachedHash);
      }
    } else {
      var nodeMap = $m_sci_Node$().bZ(mask0);
      var node = this.l1(key0, value0, originalHash0, keyHash0, key1, value1, originalHash1, keyHash1, ((5 + shift) | 0));
      var xs$2 = new $c_sjsr_WrappedVarArgs([node]);
      var array$6 = new $ac_O(xs$2.l());
      var iterator$2 = $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), new $c_sc_IndexedSeqView$Id(xs$2));
      var i$2 = 0;
      while ((iterator$2.br > 0)) {
        array$6.a[i$2] = iterator$2.f();
        i$2 = ((1 + i$2) | 0);
      }
      return new $c_sci_BitmapIndexedMapNode(0, nodeMap, array$6, $m_s_Array$EmptyArrays$().gO, node.M(), node.bS());
    }
  }
});
$p.hK = (function() {
  return (this.az !== 0);
});
$p.hO = (function() {
  return $m_jl_Integer$().bR(this.az);
});
$p.gs = (function() {
  return (this.ac !== 0);
});
$p.hQ = (function() {
  return $m_jl_Integer$().bR(this.ac);
});
$p.dl = (function(bitpos) {
  return $m_jl_Integer$().bR((this.ac & ((bitpos - 1) | 0)));
});
$p.fH = (function(bitpos) {
  return $m_jl_Integer$().bR((this.az & ((bitpos - 1) | 0)));
});
$p.qk = (function(bitpos, newKey, newValue) {
  var dataIx = this.dl(bitpos);
  var idx = (dataIx << 1);
  var src = this.b1;
  var dst = new $ac_O(src.a.length);
  var length = src.a.length;
  src.s(0, dst, 0, length);
  dst.a[((1 + idx) | 0)] = newValue;
  return new $c_sci_BitmapIndexedMapNode(this.ac, this.az, dst, this.ch, this.bs, this.c3);
});
$p.qi = (function(bitpos, oldNode, newNode) {
  var idx = ((((this.b1.a.length - 1) | 0) - this.fH(bitpos)) | 0);
  var src = this.b1;
  var dst = new $ac_O(src.a.length);
  var length = src.a.length;
  src.s(0, dst, 0, length);
  dst.a[idx] = newNode;
  return new $c_sci_BitmapIndexedMapNode(this.ac, this.az, dst, this.ch, ((((this.bs - oldNode.M()) | 0) + newNode.M()) | 0), ((((this.c3 - oldNode.bS()) | 0) + newNode.bS()) | 0));
});
$p.qe = (function(bitpos, key, originalHash, keyHash, value) {
  var dataIx = this.dl(bitpos);
  var idx = (dataIx << 1);
  var src = this.b1;
  var dst = new $ac_O(((2 + src.a.length) | 0));
  src.s(0, dst, 0, idx);
  dst.a[idx] = key;
  dst.a[((1 + idx) | 0)] = value;
  var destPos = ((2 + idx) | 0);
  var length = ((src.a.length - idx) | 0);
  src.s(idx, dst, destPos, length);
  var dstHashes = this.ot(this.ch, dataIx, originalHash);
  return new $c_sci_BitmapIndexedMapNode((this.ac | bitpos), this.az, dst, dstHashes, ((1 + this.bs) | 0), ((this.c3 + keyHash) | 0));
});
$p.rw = (function(bitpos, keyHash, node) {
  var dataIx = this.dl(bitpos);
  var idxOld = (dataIx << 1);
  var idxNew = ((((this.b1.a.length - 2) | 0) - this.fH(bitpos)) | 0);
  var src = this.b1;
  var dst = new $ac_O(((src.a.length - 1) | 0));
  src.s(0, dst, 0, idxOld);
  var srcPos = ((2 + idxOld) | 0);
  var length = ((idxNew - idxOld) | 0);
  src.s(srcPos, dst, idxOld, length);
  dst.a[idxNew] = node;
  var srcPos$1 = ((2 + idxNew) | 0);
  var destPos = ((1 + idxNew) | 0);
  var length$1 = ((((src.a.length - idxNew) | 0) - 2) | 0);
  src.s(srcPos$1, dst, destPos, length$1);
  var dstHashes = this.j4(this.ch, dataIx);
  this.ac = (this.ac ^ bitpos);
  this.az = (this.az | bitpos);
  this.b1 = dst;
  this.ch = dstHashes;
  this.bs = ((((this.bs - 1) | 0) + node.M()) | 0);
  this.c3 = ((((this.c3 - keyHash) | 0) + node.bS()) | 0);
  return this;
});
$p.qg = (function(bitpos, keyHash, node) {
  var dataIx = this.dl(bitpos);
  var idxOld = (dataIx << 1);
  var idxNew = ((((this.b1.a.length - 2) | 0) - this.fH(bitpos)) | 0);
  var src = this.b1;
  var dst = new $ac_O(((src.a.length - 1) | 0));
  src.s(0, dst, 0, idxOld);
  var srcPos = ((2 + idxOld) | 0);
  var length = ((idxNew - idxOld) | 0);
  src.s(srcPos, dst, idxOld, length);
  dst.a[idxNew] = node;
  var srcPos$1 = ((2 + idxNew) | 0);
  var destPos = ((1 + idxNew) | 0);
  var length$1 = ((((src.a.length - idxNew) | 0) - 2) | 0);
  src.s(srcPos$1, dst, destPos, length$1);
  var dstHashes = this.j4(this.ch, dataIx);
  return new $c_sci_BitmapIndexedMapNode((this.ac ^ bitpos), (this.az | bitpos), dst, dstHashes, ((((this.bs - 1) | 0) + node.M()) | 0), ((((this.c3 - keyHash) | 0) + node.bS()) | 0));
});
$p.dm = (function(f) {
  var iN = $m_jl_Integer$().bR(this.ac);
  var i$1 = 0;
  while ((i$1 < iN)) {
    f.ca(this.dP(i$1), this.dp(i$1));
    i$1 = ((1 + i$1) | 0);
  }
  var jN = $m_jl_Integer$().bR(this.az);
  var j = 0;
  while ((j < jN)) {
    this.dn(j).dm(f);
    j = ((1 + j) | 0);
  }
});
$p.kG = (function(f) {
  var i = 0;
  var iN = $m_jl_Integer$().bR(this.ac);
  while ((i < iN)) {
    f.nR(this.dP(i), this.dp(i), this.co(i));
    i = ((1 + i) | 0);
  }
  var jN = $m_jl_Integer$().bR(this.az);
  var j = 0;
  while ((j < jN)) {
    this.dn(j).kG(f);
    j = ((1 + j) | 0);
  }
});
$p.p = (function(that) {
  if ((that instanceof $c_sci_BitmapIndexedMapNode)) {
    if ((this === that)) {
      return true;
    } else if ((((((this.c3 === that.c3) && (this.az === that.az)) && (this.ac === that.ac)) && (this.bs === that.bs)) && $m_ju_Arrays$().iS(this.ch, that.ch))) {
      var a1 = this.b1;
      var a2 = that.b1;
      var length = this.b1.a.length;
      if ((a1 === a2)) {
        return true;
      } else {
        var isEqual = true;
        var i = 0;
        while ((isEqual && (i < length))) {
          isEqual = $m_sr_BoxesRunTime$().n(a1.a[i], a2.a[i]);
          i = ((1 + i) | 0);
        }
        return isEqual;
      }
    } else {
      return false;
    }
  } else {
    return false;
  }
});
$p.u = (function() {
  throw new $c_jl_UnsupportedOperationException("Trie nodes do not support hashing.");
});
$p.t = (function() {
  var i = $systemIdentityHashCode(this);
  return (($objectClassName(this) + "@") + (i >>> 0.0).toString(16));
});
$p.nY = (function() {
  var this$1 = this.b1;
  var contentClone = this$1.h();
  var contentLength = contentClone.a.length;
  var i$1 = ($m_jl_Integer$().bR(this.ac) << 1);
  while ((i$1 < contentLength)) {
    contentClone.a[i$1] = contentClone.a[i$1].o0();
    i$1 = ((1 + i$1) | 0);
  }
  return new $c_sci_BitmapIndexedMapNode(this.ac, this.az, contentClone, this.ch.h(), this.bs, this.c3);
});
$p.hJ = (function(index) {
  return this.dn(index);
});
$p.p6 = (function(key, value, originalHash, hash, shift, replaceValue) {
  return this.p5(key, value, originalHash, hash, shift, replaceValue);
});
$p.o0 = (function() {
  return this.nY();
});
function $isArrayOf_sci_BitmapIndexedMapNode(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cb)));
}
var $d_sci_BitmapIndexedMapNode = new $TypeData().i($c_sci_BitmapIndexedMapNode, "scala.collection.immutable.BitmapIndexedMapNode", ({
  cb: 1,
  cm: 1,
  aF: 1
}));
/** @constructor */
function $c_sci_BitmapIndexedSetNode(dataMap, nodeMap, content, originalHashes, size, cachedJavaKeySetHashCode) {
  this.a5 = 0;
  this.aO = 0;
  this.bm = null;
  this.cw = null;
  this.bt = 0;
  this.ci = 0;
  this.a5 = dataMap;
  this.aO = nodeMap;
  this.bm = content;
  this.cw = originalHashes;
  this.bt = size;
  this.ci = cachedJavaKeySetHashCode;
}
$p = $c_sci_BitmapIndexedSetNode.prototype = new $h_sci_SetNode();
$p.constructor = $c_sci_BitmapIndexedSetNode;
/** @constructor */
function $h_sci_BitmapIndexedSetNode() {
}
$h_sci_BitmapIndexedSetNode.prototype = $p;
$p.M = (function() {
  return this.bt;
});
$p.bS = (function() {
  return this.ci;
});
$p.dR = (function(index) {
  return this.bm.a[index];
});
$p.co = (function(index) {
  return this.cw.a[index];
});
$p.dQ = (function(index) {
  return this.bm.a[((((this.bm.a.length - 1) | 0) - index) | 0)];
});
$p.hE = (function(element, originalHash, elementHash, shift) {
  var mask = $m_sci_Node$().cH(elementHash, shift);
  var bitpos = $m_sci_Node$().bZ(mask);
  if (((this.a5 & bitpos) !== 0)) {
    var index = $m_sci_Node$().bL(this.a5, mask, bitpos);
    return ((this.cw.a[index] === originalHash) && $m_sr_BoxesRunTime$().n(element, this.dR(index)));
  }
  if (((this.aO & bitpos) !== 0)) {
    return this.dQ($m_sci_Node$().bL(this.aO, mask, bitpos)).hE(element, originalHash, elementHash, ((5 + shift) | 0));
  }
  return false;
});
$p.p3 = (function(element, originalHash, elementHash, shift) {
  var mask = $m_sci_Node$().cH(elementHash, shift);
  var bitpos = $m_sci_Node$().bZ(mask);
  if (((this.a5 & bitpos) !== 0)) {
    var index = $m_sci_Node$().bL(this.a5, mask, bitpos);
    var element0 = this.dR(index);
    if (Object.is(element0, element)) {
      return this;
    } else {
      var element0UnimprovedHash = this.co(index);
      var element0Hash = $m_sc_Hashing$().bD(element0UnimprovedHash);
      if (((originalHash === element0UnimprovedHash) && $m_sr_BoxesRunTime$().n(element0, element))) {
        return this;
      } else {
        return this.qh(bitpos, element0Hash, this.l0(element0, element0UnimprovedHash, element0Hash, element, originalHash, elementHash, ((5 + shift) | 0)));
      }
    }
  }
  if (((this.aO & bitpos) !== 0)) {
    var index$2 = $m_sci_Node$().bL(this.aO, mask, bitpos);
    var subNode = this.dQ(index$2);
    var subNodeNew$2 = subNode.p4(element, originalHash, elementHash, ((5 + shift) | 0));
    if ((subNode === subNodeNew$2)) {
      return this;
    } else {
      return this.qj(bitpos, subNode, subNodeNew$2);
    }
  }
  return this.qf(bitpos, element, originalHash, elementHash);
});
$p.l0 = (function(key0, originalKeyHash0, keyHash0, key1, originalKeyHash1, keyHash1, shift) {
  if ((shift >= 32)) {
    return new $c_sci_HashCollisionSetNode(originalKeyHash0, keyHash0, $m_sci_Vector$().iU(new $c_sjsr_WrappedVarArgs([key0, key1])));
  } else {
    var mask0 = $m_sci_Node$().cH(keyHash0, shift);
    var mask1 = $m_sci_Node$().cH(keyHash1, shift);
    if ((mask0 !== mask1)) {
      var dataMap = ($m_sci_Node$().bZ(mask0) | $m_sci_Node$().bZ(mask1));
      var newCachedHashCode = ((keyHash0 + keyHash1) | 0);
      if ((mask0 < mask1)) {
        var xs = new $c_sjsr_WrappedVarArgs([key0, key1]);
        var array$2 = new $ac_O(xs.l());
        var iterator = $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), new $c_sc_IndexedSeqView$Id(xs));
        var i = 0;
        while ((iterator.br > 0)) {
          array$2.a[i] = iterator.f();
          i = ((1 + i) | 0);
        }
        return new $c_sci_BitmapIndexedSetNode(dataMap, 0, array$2, new $ac_I(new Int32Array([originalKeyHash0, originalKeyHash1])), 2, newCachedHashCode);
      } else {
        var xs$1 = new $c_sjsr_WrappedVarArgs([key1, key0]);
        var array$4 = new $ac_O(xs$1.l());
        var iterator$1 = $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), new $c_sc_IndexedSeqView$Id(xs$1));
        var i$1 = 0;
        while ((iterator$1.br > 0)) {
          array$4.a[i$1] = iterator$1.f();
          i$1 = ((1 + i$1) | 0);
        }
        return new $c_sci_BitmapIndexedSetNode(dataMap, 0, array$4, new $ac_I(new Int32Array([originalKeyHash1, originalKeyHash0])), 2, newCachedHashCode);
      }
    } else {
      var nodeMap = $m_sci_Node$().bZ(mask0);
      var node = this.l0(key0, originalKeyHash0, keyHash0, key1, originalKeyHash1, keyHash1, ((5 + shift) | 0));
      var xs$2 = new $c_sjsr_WrappedVarArgs([node]);
      var array$6 = new $ac_O(xs$2.l());
      var iterator$2 = $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), new $c_sc_IndexedSeqView$Id(xs$2));
      var i$2 = 0;
      while ((iterator$2.br > 0)) {
        array$6.a[i$2] = iterator$2.f();
        i$2 = ((1 + i$2) | 0);
      }
      return new $c_sci_BitmapIndexedSetNode(0, nodeMap, array$6, $m_s_Array$EmptyArrays$().gO, node.M(), node.bS());
    }
  }
});
$p.gs = (function() {
  return (this.a5 !== 0);
});
$p.hQ = (function() {
  return $m_jl_Integer$().bR(this.a5);
});
$p.hK = (function() {
  return (this.aO !== 0);
});
$p.hO = (function() {
  return $m_jl_Integer$().bR(this.aO);
});
$p.dl = (function(bitpos) {
  return $m_jl_Integer$().bR((this.a5 & ((bitpos - 1) | 0)));
});
$p.fH = (function(bitpos) {
  return $m_jl_Integer$().bR((this.aO & ((bitpos - 1) | 0)));
});
$p.qj = (function(bitpos, oldNode, newNode) {
  var idx = ((((this.bm.a.length - 1) | 0) - this.fH(bitpos)) | 0);
  var src = this.bm;
  var dst = new $ac_O(src.a.length);
  var length = src.a.length;
  src.s(0, dst, 0, length);
  dst.a[idx] = newNode;
  return new $c_sci_BitmapIndexedSetNode(this.a5, this.aO, dst, this.cw, ((((this.bt - oldNode.M()) | 0) + newNode.M()) | 0), ((((this.ci - oldNode.bS()) | 0) + newNode.bS()) | 0));
});
$p.qf = (function(bitpos, key, originalHash, elementHash) {
  var dataIx = this.dl(bitpos);
  var src = this.bm;
  var dst = new $ac_O(((1 + src.a.length) | 0));
  src.s(0, dst, 0, dataIx);
  dst.a[dataIx] = key;
  var destPos = ((1 + dataIx) | 0);
  var length = ((src.a.length - dataIx) | 0);
  src.s(dataIx, dst, destPos, length);
  var dstHashes = this.ot(this.cw, dataIx, originalHash);
  return new $c_sci_BitmapIndexedSetNode((this.a5 | bitpos), this.aO, dst, dstHashes, ((1 + this.bt) | 0), ((this.ci + elementHash) | 0));
});
$p.qh = (function(bitpos, elementHash, node) {
  var dataIx = this.dl(bitpos);
  var idxNew = ((((this.bm.a.length - 1) | 0) - this.fH(bitpos)) | 0);
  var src = this.bm;
  var dst = new $ac_O(src.a.length);
  src.s(0, dst, 0, dataIx);
  var srcPos = ((1 + dataIx) | 0);
  var length = ((idxNew - dataIx) | 0);
  src.s(srcPos, dst, dataIx, length);
  dst.a[idxNew] = node;
  var srcPos$1 = ((1 + idxNew) | 0);
  var destPos = ((1 + idxNew) | 0);
  var length$1 = ((((src.a.length - idxNew) | 0) - 1) | 0);
  src.s(srcPos$1, dst, destPos, length$1);
  var dstHashes = this.j4(this.cw, dataIx);
  return new $c_sci_BitmapIndexedSetNode((this.a5 ^ bitpos), (this.aO | bitpos), dst, dstHashes, ((((this.bt - 1) | 0) + node.M()) | 0), ((((this.ci - elementHash) | 0) + node.bS()) | 0));
});
$p.rx = (function(bitpos, keyHash, node) {
  var dataIx = this.dl(bitpos);
  var idxNew = ((((this.bm.a.length - 1) | 0) - this.fH(bitpos)) | 0);
  var src = this.bm;
  var srcPos = ((1 + dataIx) | 0);
  var dest = this.bm;
  var length = ((idxNew - dataIx) | 0);
  src.s(srcPos, dest, dataIx, length);
  this.bm.a[idxNew] = node;
  this.a5 = (this.a5 ^ bitpos);
  this.aO = (this.aO | bitpos);
  this.cw = this.j4(this.cw, dataIx);
  this.bt = ((((this.bt - 1) | 0) + node.M()) | 0);
  this.ci = ((((this.ci - keyHash) | 0) + node.bS()) | 0);
  return this;
});
$p.la = (function(that, shift) {
  if ((this === that)) {
    return true;
  } else {
    if ((that instanceof $c_sci_HashCollisionSetNode)) {
      return false;
    }
    if ((that instanceof $c_sci_BitmapIndexedSetNode)) {
      var thisBitmap = (this.a5 | this.aO);
      var nodeBitmap = (that.a5 | that.aO);
      if (((thisBitmap | nodeBitmap) !== nodeBitmap)) {
        return false;
      }
      var bitmap = (thisBitmap & nodeBitmap);
      var i = bitmap;
      var bitsToSkip = ((32 - Math.clz32(((~i) & ((i - 1) | 0)))) | 0);
      var isValidSubset = true;
      while ((isValidSubset && (bitsToSkip < 32))) {
        var bitpos = $m_sci_Node$().bZ(bitsToSkip);
        if (((this.a5 & bitpos) !== 0)) {
          if (((that.a5 & bitpos) !== 0)) {
            isValidSubset = $m_sr_BoxesRunTime$().n(this.dR($m_sci_Node$().fF(this.a5, bitpos)), that.dR($m_sci_Node$().fF(that.a5, bitpos)));
          } else {
            var thisDataIndex = $m_sci_Node$().fF(this.a5, bitpos);
            var payload = this.dR(thisDataIndex);
            var subNode = that.dQ($m_sci_Node$().fF(that.aO, bitpos));
            var elementUnimprovedHash = this.co(thisDataIndex);
            var elementHash = $m_sc_Hashing$().bD(elementUnimprovedHash);
            isValidSubset = subNode.hE(payload, elementUnimprovedHash, elementHash, ((5 + shift) | 0));
          }
        } else if (((that.a5 & bitpos) === 0)) {
          var subNode0 = this.dQ($m_sci_Node$().fF(this.aO, bitpos));
          var subNode1 = that.dQ($m_sci_Node$().fF(that.aO, bitpos));
          isValidSubset = subNode0.la(subNode1, ((5 + shift) | 0));
        } else {
          isValidSubset = false;
        }
        var newBitmap = (bitmap ^ bitpos);
        bitmap = newBitmap;
        bitsToSkip = ((32 - Math.clz32(((~newBitmap) & ((newBitmap - 1) | 0)))) | 0);
      }
      return isValidSubset;
    }
    throw new $c_s_MatchError(that);
  }
});
$p.p = (function(that) {
  if ((that instanceof $c_sci_BitmapIndexedSetNode)) {
    if ((this === that)) {
      return true;
    } else if ((((((this.ci === that.ci) && (this.aO === that.aO)) && (this.a5 === that.a5)) && (this.bt === that.bt)) && $m_ju_Arrays$().iS(this.cw, that.cw))) {
      var a1 = this.bm;
      var a2 = that.bm;
      var length = this.bm.a.length;
      if ((a1 === a2)) {
        return true;
      } else {
        var isEqual = true;
        var i = 0;
        while ((isEqual && (i < length))) {
          isEqual = $m_sr_BoxesRunTime$().n(a1.a[i], a2.a[i]);
          i = ((1 + i) | 0);
        }
        return isEqual;
      }
    } else {
      return false;
    }
  } else {
    return false;
  }
});
$p.u = (function() {
  throw new $c_jl_UnsupportedOperationException("Trie nodes do not support hashing.");
});
$p.t = (function() {
  return $m_sc_StringOps$().qS("BitmapIndexedSetNode(size=%s, dataMap=%x, nodeMap=%x)", $m_sr_ScalaRunTime$().r1(new $ac_O([this.bt, this.a5, this.aO])));
});
$p.nZ = (function() {
  var this$1 = this.bm;
  var contentClone = this$1.h();
  var contentLength = contentClone.a.length;
  var i$1 = $m_jl_Integer$().bR(this.a5);
  while ((i$1 < contentLength)) {
    contentClone.a[i$1] = contentClone.a[i$1].o1();
    i$1 = ((1 + i$1) | 0);
  }
  return new $c_sci_BitmapIndexedSetNode(this.a5, this.aO, contentClone, this.cw.h(), this.bt, this.ci);
});
$p.kF = (function(f) {
  var iN = $m_jl_Integer$().bR(this.a5);
  var i$1 = 0;
  while ((i$1 < iN)) {
    f.ca(this.dR(i$1), this.co(i$1));
    i$1 = ((1 + i$1) | 0);
  }
  var jN = $m_jl_Integer$().bR(this.aO);
  var j = 0;
  while ((j < jN)) {
    this.dQ(j).kF(f);
    j = ((1 + j) | 0);
  }
});
$p.hJ = (function(index) {
  return this.dQ(index);
});
$p.p4 = (function(element, originalHash, hash, shift) {
  return this.p3(element, originalHash, hash, shift);
});
$p.o1 = (function() {
  return this.nZ();
});
function $isArrayOf_sci_BitmapIndexedSetNode(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cc)));
}
var $d_sci_BitmapIndexedSetNode = new $TypeData().i($c_sci_BitmapIndexedSetNode, "scala.collection.immutable.BitmapIndexedSetNode", ({
  cc: 1,
  cr: 1,
  aF: 1
}));
/** @constructor */
function $c_sci_HashCollisionMapNode(originalHash, hash, content) {
  this.is = 0;
  this.eB = 0;
  this.aP = null;
  this.is = originalHash;
  this.eB = hash;
  this.aP = content;
  $m_s_Predef$().oG((this.aP.l() >= 2));
}
$p = $c_sci_HashCollisionMapNode.prototype = new $h_sci_MapNode();
$p.constructor = $c_sci_HashCollisionMapNode;
/** @constructor */
function $h_sci_HashCollisionMapNode() {
}
$h_sci_HashCollisionMapNode.prototype = $p;
$p.gu = (function(key) {
  var iter = this.aP.c();
  var i = 0;
  while (iter.k()) {
    if ($m_sr_BoxesRunTime$().n(iter.f().aw(), key)) {
      return i;
    }
    i = ((1 + i) | 0);
  }
  return (-1);
});
$p.M = (function() {
  return this.aP.l();
});
$p.ko = (function(key, originalHash, hash, shift) {
  var this$1 = this.iV(key, originalHash, hash, shift);
  return (this$1.g() ? $m_sc_Iterator$().G.f() : this$1.S());
});
$p.iV = (function(key, originalHash, hash, shift) {
  if ((this.eB === hash)) {
    var index = this.gu(key);
    return ((index >= 0) ? new $c_s_Some(this.aP.r(index).ap()) : $m_s_None$());
  } else {
    return $m_s_None$();
  }
});
$p.kN = (function(key, originalHash, hash, shift, f) {
  if ((this.eB === hash)) {
    var x36 = this.gu(key);
    if ((x36 === (-1))) {
      return f.a2();
    }
    return this.aP.r(x36).ap();
  } else {
    return f.a2();
  }
});
$p.ku = (function(key, originalHash, hash, shift) {
  return ((this.eB === hash) && (this.gu(key) >= 0));
});
$p.p6 = (function(key, value, originalHash, hash, shift, replaceValue) {
  var index = this.gu(key);
  return ((index >= 0) ? (replaceValue ? (Object.is(this.aP.r(index).ap(), value) ? this : new $c_sci_HashCollisionMapNode(originalHash, hash, this.aP.er(index, $ct_T2__O__O__(new $c_T2(), key, value)))) : this) : new $c_sci_HashCollisionMapNode(originalHash, hash, this.aP.dM($ct_T2__O__O__(new $c_T2(), key, value))));
});
$p.hK = (function() {
  return false;
});
$p.hO = (function() {
  return 0;
});
$p.dn = (function(index) {
  throw $ct_jl_IndexOutOfBoundsException__T__(new $c_jl_IndexOutOfBoundsException(), "No sub-nodes present in hash-collision leaf node.");
});
$p.gs = (function() {
  return true;
});
$p.hQ = (function() {
  return this.aP.l();
});
$p.dP = (function(index) {
  return this.aP.r(index).aw();
});
$p.dp = (function(index) {
  return this.aP.r(index).ap();
});
$p.op = (function(index) {
  return this.aP.r(index);
});
$p.co = (function(index) {
  return this.is;
});
$p.dm = (function(f) {
  this.aP.dO(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$2) => {
    var k = x$1$2.aw();
    var v = x$1$2.ap();
    return f.ca(k, v);
  })));
});
$p.kG = (function(f) {
  var iter = this.aP.c();
  while (iter.k()) {
    var next = iter.f();
    f.nR(next.aw(), next.ap(), this.is);
  }
});
$p.p = (function(that) {
  if ((that instanceof $c_sci_HashCollisionMapNode)) {
    if ((this === that)) {
      return true;
    } else if (((this.eB === that.eB) && (this.aP.l() === that.aP.l()))) {
      var iter = this.aP.c();
      while (iter.k()) {
        var x47 = iter.f();
        x47.aw();
        x47.ap();
        var \u03b412$ = x47;
        var key$2 = \u03b412$.aw();
        var value$2 = \u03b412$.ap();
        var index = that.gu(key$2);
        if (((index < 0) || (!$m_sr_BoxesRunTime$().n(value$2, that.aP.r(index).ap())))) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  } else {
    return false;
  }
});
$p.u = (function() {
  throw new $c_jl_UnsupportedOperationException("Trie nodes do not support hashing.");
});
$p.t = (function() {
  var i = $systemIdentityHashCode(this);
  return (($objectClassName(this) + "@") + (i >>> 0.0).toString(16));
});
$p.bS = (function() {
  return Math.imul(this.aP.l(), this.eB);
});
$p.hJ = (function(index) {
  return this.dn(index);
});
$p.o0 = (function() {
  return new $c_sci_HashCollisionMapNode(this.is, this.eB, this.aP);
});
function $isArrayOf_sci_HashCollisionMapNode(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cd)));
}
var $d_sci_HashCollisionMapNode = new $TypeData().i($c_sci_HashCollisionMapNode, "scala.collection.immutable.HashCollisionMapNode", ({
  cd: 1,
  cm: 1,
  aF: 1
}));
/** @constructor */
function $c_sci_HashCollisionSetNode(originalHash, hash, content) {
  this.it = 0;
  this.gc = 0;
  this.b2 = null;
  this.it = originalHash;
  this.gc = hash;
  this.b2 = content;
  $m_s_Predef$().oG((this.b2.l() >= 2));
}
$p = $c_sci_HashCollisionSetNode.prototype = new $h_sci_SetNode();
$p.constructor = $c_sci_HashCollisionSetNode;
/** @constructor */
function $h_sci_HashCollisionSetNode() {
}
$h_sci_HashCollisionSetNode.prototype = $p;
$p.hE = (function(element, originalHash, hash, shift) {
  return ((this.gc === hash) && $f_sc_SeqOps__contains__O__Z(this.b2, element));
});
$p.p4 = (function(element, originalHash, hash, shift) {
  return (this.hE(element, originalHash, hash, shift) ? this : new $c_sci_HashCollisionSetNode(originalHash, hash, this.b2.dM(element)));
});
$p.hK = (function() {
  return false;
});
$p.hO = (function() {
  return 0;
});
$p.dQ = (function(index) {
  throw $ct_jl_IndexOutOfBoundsException__T__(new $c_jl_IndexOutOfBoundsException(), "No sub-nodes present in hash-collision leaf node.");
});
$p.gs = (function() {
  return true;
});
$p.hQ = (function() {
  return this.b2.l();
});
$p.dR = (function(index) {
  return this.b2.r(index);
});
$p.co = (function(index) {
  return this.it;
});
$p.M = (function() {
  return this.b2.l();
});
$p.bS = (function() {
  return Math.imul(this.b2.l(), this.gc);
});
$p.la = (function(that, shift) {
  if ((this === that)) {
    return true;
  } else if ((that instanceof $c_sci_HashCollisionSetNode)) {
    if ((this.b2.l() <= that.b2.l())) {
      var this$1 = this.b2;
      var \u03b41$ = that.b2;
      var res = true;
      var it = this$1.c();
      while ((res && it.k())) {
        res = $f_sc_SeqOps__contains__O__Z(\u03b41$, it.f());
      }
      return res;
    } else {
      return false;
    }
  } else {
    return false;
  }
});
$p.p = (function(that) {
  if ((that instanceof $c_sci_HashCollisionSetNode)) {
    if ((this === that)) {
      return true;
    } else if (((this.gc === that.gc) && (this.b2.l() === that.b2.l()))) {
      var this$3 = this.b2;
      var \u03b42$ = that.b2;
      var res = true;
      var it = this$3.c();
      while ((res && it.k())) {
        res = $f_sc_SeqOps__contains__O__Z(\u03b42$, it.f());
      }
      return res;
    } else {
      return false;
    }
  } else {
    return false;
  }
});
$p.u = (function() {
  throw new $c_jl_UnsupportedOperationException("Trie nodes do not support hashing.");
});
$p.kF = (function(f) {
  var iter = this.b2.c();
  while (iter.k()) {
    var next = iter.f();
    f.ca(next, this.it);
  }
});
$p.hJ = (function(index) {
  return this.dQ(index);
});
$p.o1 = (function() {
  return new $c_sci_HashCollisionSetNode(this.it, this.gc, this.b2);
});
function $isArrayOf_sci_HashCollisionSetNode(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.ce)));
}
var $d_sci_HashCollisionSetNode = new $TypeData().i($c_sci_HashCollisionSetNode, "scala.collection.immutable.HashCollisionSetNode", ({
  ce: 1,
  cr: 1,
  aF: 1
}));
/** @constructor */
function $c_sci_HashMap$() {
  this.h4 = null;
  $n_sci_HashMap$ = this;
  this.h4 = new $c_sci_HashMap($m_sci_MapNode$().mW);
}
$p = $c_sci_HashMap$.prototype = new $h_O();
$p.constructor = $c_sci_HashMap$;
/** @constructor */
function $h_sci_HashMap$() {
}
$h_sci_HashMap$.prototype = $p;
$p.qU = (function(source) {
  return ((source instanceof $c_sci_HashMap) ? source : new $c_sci_HashMapBuilder().kl(source).l3());
});
$p.hF = (function() {
  return this.h4;
});
$p.a3 = (function(it) {
  return this.qU(it);
});
$p.T = (function() {
  return new $c_sci_HashMapBuilder();
});
var $d_sci_HashMap$ = new $TypeData().i($c_sci_HashMap$, "scala.collection.immutable.HashMap$", ({
  fD: 1,
  a: 1,
  aC: 1
}));
var $n_sci_HashMap$;
function $m_sci_HashMap$() {
  if ((!$n_sci_HashMap$)) {
    $n_sci_HashMap$ = new $c_sci_HashMap$();
  }
  return $n_sci_HashMap$;
}
/** @constructor */
function $c_sci_HashSet$() {
  this.iu = null;
  $n_sci_HashSet$ = this;
  this.iu = new $c_sci_HashSet($m_sci_SetNode$().n1);
}
$p = $c_sci_HashSet$.prototype = new $h_O();
$p.constructor = $c_sci_HashSet$;
/** @constructor */
function $h_sci_HashSet$() {
}
$h_sci_HashSet$.prototype = $p;
$p.qV = (function(source) {
  return ((source instanceof $c_sci_HashSet) ? source : ((source.q() === 0) ? this.iu : new $c_sci_HashSetBuilder().km(source).l4()));
});
$p.a3 = (function(source) {
  return this.qV(source);
});
$p.T = (function() {
  return new $c_sci_HashSetBuilder();
});
var $d_sci_HashSet$ = new $TypeData().i($c_sci_HashSet$, "scala.collection.immutable.HashSet$", ({
  fH: 1,
  a: 1,
  B: 1
}));
var $n_sci_HashSet$;
function $m_sci_HashSet$() {
  if ((!$n_sci_HashSet$)) {
    $n_sci_HashSet$ = new $c_sci_HashSet$();
  }
  return $n_sci_HashSet$;
}
/** @constructor */
function $c_sci_Map$() {
}
$p = $c_sci_Map$.prototype = new $h_O();
$p.constructor = $c_sci_Map$;
/** @constructor */
function $h_sci_Map$() {
}
$h_sci_Map$.prototype = $p;
$p.hI = (function(it) {
  if ($is_sci_Iterable(it)) {
    if (it.g()) {
      return $m_sci_Map$EmptyMap$();
    }
  }
  if ((it instanceof $c_sci_HashMap)) {
    return it;
  }
  if ((it instanceof $c_sci_Map$Map1)) {
    return it;
  }
  if ((it instanceof $c_sci_Map$Map2)) {
    return it;
  }
  if ((it instanceof $c_sci_Map$Map3)) {
    return it;
  }
  if ((it instanceof $c_sci_Map$Map4)) {
    return it;
  }
  if (false) {
    return it;
  }
  if (false) {
    return it;
  }
  if (false) {
    return it;
  }
  if (false) {
    return it;
  }
  if (false) {
    return it;
  }
  if (false) {
    return it;
  }
  if (false) {
    return it;
  }
  return new $c_sci_MapBuilderImpl().nF(it).oH();
});
$p.T = (function() {
  return new $c_sci_MapBuilderImpl();
});
$p.hF = (function() {
  return $m_sci_Map$EmptyMap$();
});
$p.a3 = (function(it) {
  return this.hI(it);
});
var $d_sci_Map$ = new $TypeData().i($c_sci_Map$, "scala.collection.immutable.Map$", ({
  fY: 1,
  a: 1,
  aC: 1
}));
var $n_sci_Map$;
function $m_sci_Map$() {
  if ((!$n_sci_Map$)) {
    $n_sci_Map$ = new $c_sci_Map$();
  }
  return $n_sci_Map$;
}
/** @constructor */
function $c_sci_Set$() {
}
$p = $c_sci_Set$.prototype = new $h_O();
$p.constructor = $c_sci_Set$;
/** @constructor */
function $h_sci_Set$() {
}
$h_sci_Set$.prototype = $p;
$p.qX = (function(it) {
  return ((it.q() === 0) ? $m_sci_Set$EmptySet$() : ((it instanceof $c_sci_HashSet) ? it : (false ? it : ((it instanceof $c_sci_Set$Set1) ? it : ((it instanceof $c_sci_Set$Set2) ? it : ((it instanceof $c_sci_Set$Set3) ? it : ((it instanceof $c_sci_Set$Set4) ? it : (false ? it : (false ? it : new $c_sci_SetBuilderImpl().nG(it).oI())))))))));
});
$p.T = (function() {
  return new $c_sci_SetBuilderImpl();
});
$p.a3 = (function(source) {
  return this.qX(source);
});
var $d_sci_Set$ = new $TypeData().i($c_sci_Set$, "scala.collection.immutable.Set$", ({
  gj: 1,
  a: 1,
  B: 1
}));
var $n_sci_Set$;
function $m_sci_Set$() {
  if ((!$n_sci_Set$)) {
    $n_sci_Set$ = new $c_sci_Set$();
  }
  return $n_sci_Set$;
}
/** @constructor */
function $c_sci_WrappedString$() {
}
$p = $c_sci_WrappedString$.prototype = new $h_O();
$p.constructor = $c_sci_WrappedString$;
/** @constructor */
function $h_sci_WrappedString$() {
}
$h_sci_WrappedString$.prototype = $p;
$p.om = (function(it) {
  var b = this.T();
  $f_scm_Builder__sizeHint__sc_IterableOnce__I__V(b, it, 0);
  b.b7(it);
  return b.aU();
});
$p.T = (function() {
  return new $c_scm_Builder$$anon$1(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$2) => new $c_sci_WrappedString(x$2))), $ct_scm_StringBuilder__(new $c_scm_StringBuilder()));
});
var $d_sci_WrappedString$ = new $TypeData().i($c_sci_WrappedString$, "scala.collection.immutable.WrappedString$", ({
  gG: 1,
  f3: 1,
  fq: 1
}));
var $n_sci_WrappedString$;
function $m_sci_WrappedString$() {
  if ((!$n_sci_WrappedString$)) {
    $n_sci_WrappedString$ = new $c_sci_WrappedString$();
  }
  return $n_sci_WrappedString$;
}
function $f_scm_Builder__sizeHint__sc_IterableOnce__I__V($thiz, coll, delta) {
  var x1 = coll.q();
  if ((x1 === (-1))) {
    return (void 0);
  }
  var that = ((x1 + delta) | 0);
  $thiz.aZ(((that < 0) ? 0 : that));
}
/** @constructor */
function $c_scm_HashMap$() {
}
$p = $c_scm_HashMap$.prototype = new $h_O();
$p.constructor = $c_scm_HashMap$;
/** @constructor */
function $h_scm_HashMap$() {
}
$h_scm_HashMap$.prototype = $p;
$p.qY = (function(it) {
  var k = it.q();
  return $ct_scm_HashMap__I__D__(new $c_scm_HashMap(), ((k > 0) ? $doubleToInt((((1 + k) | 0) / 0.75)) : 16), 0.75).nJ(it);
});
$p.T = (function() {
  return new $c_scm_HashMap$$anon$6(16, 0.75);
});
$p.hF = (function() {
  return $ct_scm_HashMap__(new $c_scm_HashMap());
});
$p.a3 = (function(it) {
  return this.qY(it);
});
var $d_scm_HashMap$ = new $TypeData().i($c_scm_HashMap$, "scala.collection.mutable.HashMap$", ({
  gW: 1,
  a: 1,
  aC: 1
}));
var $n_scm_HashMap$;
function $m_scm_HashMap$() {
  if ((!$n_scm_HashMap$)) {
    $n_scm_HashMap$ = new $c_scm_HashMap$();
  }
  return $n_scm_HashMap$;
}
/** @constructor */
function $c_scm_HashSet$() {
}
$p = $c_scm_HashSet$.prototype = new $h_O();
$p.constructor = $c_scm_HashSet$;
/** @constructor */
function $h_scm_HashSet$() {
}
$h_scm_HashSet$.prototype = $p;
$p.qZ = (function(it) {
  var k = it.q();
  return $ct_scm_HashSet__I__D__(new $c_scm_HashSet(), ((k > 0) ? $doubleToInt((((1 + k) | 0) / 0.75)) : 16), 0.75).nK(it);
});
$p.T = (function() {
  return new $c_scm_HashSet$$anon$4(16, 0.75);
});
$p.a3 = (function(source) {
  return this.qZ(source);
});
var $d_scm_HashSet$ = new $TypeData().i($c_scm_HashSet$, "scala.collection.mutable.HashSet$", ({
  h2: 1,
  a: 1,
  B: 1
}));
var $n_scm_HashSet$;
function $m_scm_HashSet$() {
  if ((!$n_scm_HashSet$)) {
    $n_scm_HashSet$ = new $c_scm_HashSet$();
  }
  return $n_scm_HashSet$;
}
/** @constructor */
function $c_s_concurrent_BatchingExecutor$SyncBatch(outer, runnable) {
  this.hh = null;
  this.hi = null;
  this.eW = 0;
  this.pl = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.pl = outer;
  $ct_s_concurrent_BatchingExecutor$AbstractBatch__jl_Runnable__Ajl_Runnable__I__(this, runnable, $m_s_concurrent_BatchingExecutorStatics$().ng, 1);
}
$p = $c_s_concurrent_BatchingExecutor$SyncBatch.prototype = new $h_s_concurrent_BatchingExecutor$AbstractBatch();
$p.constructor = $c_s_concurrent_BatchingExecutor$SyncBatch;
/** @constructor */
function $h_s_concurrent_BatchingExecutor$SyncBatch() {
}
$h_s_concurrent_BatchingExecutor$SyncBatch.prototype = $p;
$p.fJ = (function() {
  while (true) {
    try {
      this.rS(1024);
    } catch (e) {
      var e$2 = ((e instanceof $c_jl_Throwable) ? e : new $c_sjs_js_JavaScriptException(e));
      if (false) {
        $m_s_concurrent_ExecutionContext$().gl.d(e$2);
      } else {
        matchResult2: {
          if ($m_s_util_control_NonFatal$().iR(e$2)) {
            $m_s_concurrent_ExecutionContext$().gl.d(e$2);
            break matchResult2;
          }
          throw ((e$2 instanceof $c_sjs_js_JavaScriptException) ? e$2.di : e$2);
        }
      }
    }
    if ((this.eW > 0)) {
    } else {
      return (void 0);
    }
  }
});
function $isArrayOf_s_concurrent_BatchingExecutor$SyncBatch(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cK)));
}
var $d_s_concurrent_BatchingExecutor$SyncBatch = new $TypeData().i($c_s_concurrent_BatchingExecutor$SyncBatch, "scala.concurrent.BatchingExecutor$SyncBatch", ({
  cK: 1,
  hm: 1,
  aS: 1
}));
/** @constructor */
function $c_s_concurrent_impl_Promise$Link(to) {
  this.ab = null;
  $ct_ju_concurrent_atomic_AtomicReference__O__(this, to);
}
$p = $c_s_concurrent_impl_Promise$Link.prototype = new $h_ju_concurrent_atomic_AtomicReference();
$p.constructor = $c_s_concurrent_impl_Promise$Link;
/** @constructor */
function $h_s_concurrent_impl_Promise$Link() {
}
$h_s_concurrent_impl_Promise$Link.prototype = $p;
$p.gx = (function(owner) {
  var c = this.ab;
  var target$tailLocal1 = c;
  var current$tailLocal1 = c;
  while (true) {
    var value = target$tailLocal1.ab;
    if ($is_s_concurrent_impl_Promise$Callbacks(value)) {
      if (this.hD(current$tailLocal1, target$tailLocal1)) {
        return target$tailLocal1;
      } else {
        current$tailLocal1 = this.ab;
      }
    } else if ((value instanceof $c_s_concurrent_impl_Promise$Link)) {
      target$tailLocal1 = value.ab;
    } else {
      owner.so(value);
      return owner;
    }
  }
});
function $isArrayOf_s_concurrent_impl_Promise$Link(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cQ)));
}
var $d_s_concurrent_impl_Promise$Link = new $TypeData().i($c_s_concurrent_impl_Promise$Link, "scala.concurrent.impl.Promise$Link", ({
  cQ: 1,
  aU: 1,
  a: 1
}));
function $isArrayOf_s_math_ScalaNumber(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.hW)));
}
/** @constructor */
function $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c(f) {
  this.ns = null;
  this.ns = f;
}
$p = $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c.prototype = new $h_sr_AbstractFunction0();
$p.constructor = $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c;
/** @constructor */
function $h_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c() {
}
$h_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c.prototype = $p;
$p.a2 = (function() {
  return (0, this.ns)();
});
var $d_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c = new $TypeData().i($c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c, "scala.runtime.AbstractFunction0.$$Lambda$07eded5776954a9c145e92c329afd52873ad179c", ({
  ip: 1,
  io: 1,
  eG: 1
}));
/** @constructor */
function $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(f) {
  this.nt = null;
  this.nt = f;
}
$p = $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919.prototype = new $h_sr_AbstractFunction1();
$p.constructor = $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919;
/** @constructor */
function $h_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919() {
}
$h_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919.prototype = $p;
$p.d = (function(x0) {
  return (0, this.nt)(x0);
});
var $d_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919 = new $TypeData().i($c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919, "scala.runtime.AbstractFunction1.$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919", ({
  iq: 1,
  cW: 1,
  i: 1
}));
/** @constructor */
function $c_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8(f) {
  this.nu = null;
  this.nu = f;
}
$p = $c_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8.prototype = new $h_sr_AbstractFunction2();
$p.constructor = $c_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8;
/** @constructor */
function $h_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8() {
}
$h_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8.prototype = $p;
$p.ca = (function(x0, x1) {
  return (0, this.nu)(x0, x1);
});
var $d_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8 = new $TypeData().i($c_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8, "scala.runtime.AbstractFunction2.$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8", ({
  ir: 1,
  cX: 1,
  aV: 1
}));
/** @constructor */
function $c_sr_AbstractFunction3_$$Lambda$d1e06cbab540de4f9f09e7182f18ea80659b9825(f) {
  this.nv = null;
  this.nv = f;
}
$p = $c_sr_AbstractFunction3_$$Lambda$d1e06cbab540de4f9f09e7182f18ea80659b9825.prototype = new $h_sr_AbstractFunction3();
$p.constructor = $c_sr_AbstractFunction3_$$Lambda$d1e06cbab540de4f9f09e7182f18ea80659b9825;
/** @constructor */
function $h_sr_AbstractFunction3_$$Lambda$d1e06cbab540de4f9f09e7182f18ea80659b9825() {
}
$h_sr_AbstractFunction3_$$Lambda$d1e06cbab540de4f9f09e7182f18ea80659b9825.prototype = $p;
$p.nR = (function(x0, x1, x2) {
  return (0, this.nv)(x0, x1, x2);
});
var $d_sr_AbstractFunction3_$$Lambda$d1e06cbab540de4f9f09e7182f18ea80659b9825 = new $TypeData().i($c_sr_AbstractFunction3_$$Lambda$d1e06cbab540de4f9f09e7182f18ea80659b9825, "scala.runtime.AbstractFunction3.$$Lambda$d1e06cbab540de4f9f09e7182f18ea80659b9825", ({
  it: 1,
  is: 1,
  eH: 1
}));
/** @constructor */
function $c_sr_AbstractPartialFunction() {
}
$p = $c_sr_AbstractPartialFunction.prototype = new $h_O();
$p.constructor = $c_sr_AbstractPartialFunction;
/** @constructor */
function $h_sr_AbstractPartialFunction() {
}
$h_sr_AbstractPartialFunction.prototype = $p;
$p.t = (function() {
  return "<function1>";
});
$p.d = (function(x) {
  return this.bQ(x, $m_s_PartialFunction$().mt);
});
$p.f1 = (function(x) {
  return (this.d(x) | 0);
});
var $d_sr_Nothing$ = new $TypeData().i(0, "scala.runtime.Nothing$", ({
  iA: 1,
  r: 1,
  a: 1
}));
/** @constructor */
function $c_sjs_js_Any$() {
}
$p = $c_sjs_js_Any$.prototype = new $h_O();
$p.constructor = $c_sjs_js_Any$;
/** @constructor */
function $h_sjs_js_Any$() {
}
$h_sjs_js_Any$.prototype = $p;
$p.r0 = (function(f) {
  return (() => f.a2());
});
$p.kM = (function(f) {
  return ((arg1$2) => f.d(arg1$2));
});
var $d_sjs_js_Any$ = new $TypeData().i($c_sjs_js_Any$, "scala.scalajs.js.Any$", ({
  iR: 1,
  iU: 1,
  iV: 1
}));
var $n_sjs_js_Any$;
function $m_sjs_js_Any$() {
  if ((!$n_sjs_js_Any$)) {
    $n_sjs_js_Any$ = new $c_sjs_js_Any$();
  }
  return $n_sjs_js_Any$;
}
function $isArrayOf_s_util_control_ControlThrowable(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.jc)));
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_InkuireMatch(prettifiedSignature, functionName, packageLocation, pageLocation, entryType, mq) {
  this.fR = null;
  this.fP = null;
  this.fQ = null;
  this.es = null;
  this.gE = null;
  this.f5 = 0;
  this.fR = prettifiedSignature;
  this.fP = functionName;
  this.fQ = packageLocation;
  this.es = pageLocation;
  this.gE = entryType;
  this.f5 = mq;
}
$p = $c_Ldotty_tools_scaladoc_InkuireMatch.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_InkuireMatch;
/** @constructor */
function $h_Ldotty_tools_scaladoc_InkuireMatch() {
}
$h_Ldotty_tools_scaladoc_InkuireMatch.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.u = (function() {
  var acc = (-889275714);
  acc = $m_sr_Statics$().x(acc, (-460577480));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.fR));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.fP));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.fQ));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.es));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.gE));
  acc = $m_sr_Statics$().x(acc, this.f5);
  return $m_sr_Statics$().bd(acc, 6);
});
$p.p = (function(x$0) {
  return ((this === x$0) || ((x$0 instanceof $c_Ldotty_tools_scaladoc_InkuireMatch) && ((((((this.f5 === x$0.f5) && (this.fR === x$0.fR)) && (this.fP === x$0.fP)) && (this.fQ === x$0.fQ)) && (this.es === x$0.es)) && (this.gE === x$0.gE))));
});
$p.t = (function() {
  return $m_sr_ScalaRunTime$().dL(this);
});
$p.bE = (function() {
  return 6;
});
$p.bG = (function() {
  return "InkuireMatch";
});
$p.bF = (function(n) {
  switch (n) {
    case 0: {
      return this.fR;
      break;
    }
    case 1: {
      return this.fP;
      break;
    }
    case 2: {
      return this.fQ;
      break;
    }
    case 3: {
      return this.es;
      break;
    }
    case 4: {
      return this.gE;
      break;
    }
    case 5: {
      return this.f5;
      break;
    }
    default: {
      throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
    }
  }
});
function $isArrayOf_Ldotty_tools_scaladoc_InkuireMatch(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bf)));
}
var $d_Ldotty_tools_scaladoc_InkuireMatch = new $TypeData().i($c_Ldotty_tools_scaladoc_InkuireMatch, "dotty.tools.scaladoc.InkuireMatch", ({
  bf: 1,
  d: 1,
  J: 1,
  a: 1
}));
/** @constructor */
function $c_Ldotty_tools_scaladoc_MatchResult(score, pageEntry, indices) {
  this.cK = 0;
  this.bW = null;
  this.dv = null;
  this.cK = score;
  this.bW = pageEntry;
  this.dv = indices;
}
$p = $c_Ldotty_tools_scaladoc_MatchResult.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_MatchResult;
/** @constructor */
function $h_Ldotty_tools_scaladoc_MatchResult() {
}
$h_Ldotty_tools_scaladoc_MatchResult.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.u = (function() {
  var acc = (-889275714);
  acc = $m_sr_Statics$().x(acc, (-1024520670));
  acc = $m_sr_Statics$().x(acc, this.cK);
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.bW));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.dv));
  return $m_sr_Statics$().bd(acc, 3);
});
$p.p = (function(x$0) {
  if ((this === x$0)) {
    return true;
  } else if ((x$0 instanceof $c_Ldotty_tools_scaladoc_MatchResult)) {
    if ((this.cK === x$0.cK)) {
      var x = this.bW;
      var x$2 = x$0.bW;
      var $x_1 = ((x === null) ? (x$2 === null) : x.p(x$2));
    } else {
      var $x_1 = false;
    }
    if ($x_1) {
      var x$3 = this.dv;
      var x$4 = x$0.dv;
      return ((x$3 === null) ? (x$4 === null) : x$3.p(x$4));
    } else {
      return false;
    }
  } else {
    return false;
  }
});
$p.t = (function() {
  return $m_sr_ScalaRunTime$().dL(this);
});
$p.bE = (function() {
  return 3;
});
$p.bG = (function() {
  return "MatchResult";
});
$p.bF = (function(n) {
  switch (n) {
    case 0: {
      return this.cK;
      break;
    }
    case 1: {
      return this.bW;
      break;
    }
    case 2: {
      return this.dv;
      break;
    }
    default: {
      throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
    }
  }
});
function $isArrayOf_Ldotty_tools_scaladoc_MatchResult(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bg)));
}
var $d_Ldotty_tools_scaladoc_MatchResult = new $TypeData().i($c_Ldotty_tools_scaladoc_MatchResult, "dotty.tools.scaladoc.MatchResult", ({
  bg: 1,
  d: 1,
  J: 1,
  a: 1
}));
/** @constructor */
function $c_Ldotty_tools_scaladoc_PageEntry(fullName, description, extraDescription, extensionTarget, location, isLocationExternal, shortName, kind, tokens) {
  this.fT = null;
  this.fS = null;
  this.f7 = null;
  this.f6 = null;
  this.f8 = null;
  this.fU = false;
  this.et = null;
  this.dw = null;
  this.fV = null;
  this.fT = fullName;
  this.fS = description;
  this.f7 = extraDescription;
  this.f6 = extensionTarget;
  this.f8 = location;
  this.fU = isLocationExternal;
  this.et = shortName;
  this.dw = kind;
  this.fV = tokens;
}
$p = $c_Ldotty_tools_scaladoc_PageEntry.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_PageEntry;
/** @constructor */
function $h_Ldotty_tools_scaladoc_PageEntry() {
}
$h_Ldotty_tools_scaladoc_PageEntry.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.u = (function() {
  var acc = (-889275714);
  acc = $m_sr_Statics$().x(acc, (-1431300349));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.fT));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.fS));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.f7));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.f6));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.f8));
  acc = $m_sr_Statics$().x(acc, (this.fU ? 1231 : 1237));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.et));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.dw));
  acc = $m_sr_Statics$().x(acc, $m_sr_Statics$().y(this.fV));
  return $m_sr_Statics$().bd(acc, 9);
});
$p.p = (function(x$0) {
  if ((this === x$0)) {
    return true;
  } else if ((x$0 instanceof $c_Ldotty_tools_scaladoc_PageEntry)) {
    if (((((((((this.fU === x$0.fU) && (this.fT === x$0.fT)) && (this.fS === x$0.fS)) && (this.f7 === x$0.f7)) && (this.f6 === x$0.f6)) && (this.f8 === x$0.f8)) && (this.et === x$0.et)) && (this.dw === x$0.dw))) {
      var x = this.fV;
      var x$2 = x$0.fV;
      return ((x === null) ? (x$2 === null) : x.p(x$2));
    } else {
      return false;
    }
  } else {
    return false;
  }
});
$p.t = (function() {
  return $m_sr_ScalaRunTime$().dL(this);
});
$p.bE = (function() {
  return 9;
});
$p.bG = (function() {
  return "PageEntry";
});
$p.bF = (function(n) {
  switch (n) {
    case 0: {
      return this.fT;
      break;
    }
    case 1: {
      return this.fS;
      break;
    }
    case 2: {
      return this.f7;
      break;
    }
    case 3: {
      return this.f6;
      break;
    }
    case 4: {
      return this.f8;
      break;
    }
    case 5: {
      return this.fU;
      break;
    }
    case 6: {
      return this.et;
      break;
    }
    case 7: {
      return this.dw;
      break;
    }
    case 8: {
      return this.fV;
      break;
    }
    default: {
      throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
    }
  }
});
function $isArrayOf_Ldotty_tools_scaladoc_PageEntry(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bi)));
}
var $d_Ldotty_tools_scaladoc_PageEntry = new $TypeData().i($c_Ldotty_tools_scaladoc_PageEntry, "dotty.tools.scaladoc.PageEntry", ({
  bi: 1,
  d: 1,
  J: 1,
  a: 1
}));
/** @constructor */
function $c_Ldotty_tools_scaladoc_utils_HTML$Attr(name) {
  this.v = null;
  this.v = name;
}
$p = $c_Ldotty_tools_scaladoc_utils_HTML$Attr.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_utils_HTML$Attr;
/** @constructor */
function $h_Ldotty_tools_scaladoc_utils_HTML$Attr() {
}
$h_Ldotty_tools_scaladoc_utils_HTML$Attr.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().dr(this, (-195397372), true);
});
$p.p = (function(x$0) {
  return ((this === x$0) || ((x$0 instanceof $c_Ldotty_tools_scaladoc_utils_HTML$Attr) && (this.v === x$0.v)));
});
$p.t = (function() {
  return $m_sr_ScalaRunTime$().dL(this);
});
$p.bE = (function() {
  return 1;
});
$p.bG = (function() {
  return "Attr";
});
$p.bF = (function(n) {
  if ((n === 0)) {
    return this.v;
  }
  throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
});
function $isArrayOf_Ldotty_tools_scaladoc_utils_HTML$Attr(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bk)));
}
var $d_Ldotty_tools_scaladoc_utils_HTML$Attr = new $TypeData().i($c_Ldotty_tools_scaladoc_utils_HTML$Attr, "dotty.tools.scaladoc.utils.HTML$Attr", ({
  bk: 1,
  d: 1,
  J: 1,
  a: 1
}));
function $p_Ldotty_tools_scaladoc_utils_HTML$Tag__textNode__T__Lorg_scalajs_dom_Text($thiz, s) {
  return document.createTextNode(s);
}
function $p_Ldotty_tools_scaladoc_utils_HTML$Tag__unpackTags$1__Lorg_scalajs_dom_HTMLElement__sci_Seq__V($thiz, elem$1, tags) {
  tags.dO(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$2) => {
    if ((!(!(x$1$2 instanceof HTMLElement)))) {
      return elem$1.appendChild(x$1$2);
    }
    if (((typeof x$1$2) === "string")) {
      return elem$1.appendChild($p_Ldotty_tools_scaladoc_utils_HTML$Tag__textNode__T__Lorg_scalajs_dom_Text($thiz, x$1$2));
    }
    if ($is_sci_Seq(x$1$2)) {
      $p_Ldotty_tools_scaladoc_utils_HTML$Tag__unpackTags$1__Lorg_scalajs_dom_HTMLElement__sci_Seq__V($thiz, elem$1, x$1$2);
      return (void 0);
    }
    throw new $c_s_MatchError(x$1$2);
  })));
}
function $p_Ldotty_tools_scaladoc_utils_HTML$Tag__unpackAttributes$1__Lorg_scalajs_dom_HTMLElement__sci_Seq__V($thiz, elem$3, attrs) {
  attrs.dO(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$3) => {
    matchResult5: {
      if ((x$1$3 instanceof $c_T2)) {
        var x18 = x$1$3.aw();
        var x19 = x$1$3.ap();
        if ((x18 === "id")) {
          elem$3.id = x19;
          break matchResult5;
        }
        if ((x18 === "class")) {
          var xs = $f_T__split__T__I__AT(x19, "\\s+", 0);
          var f = ((cls$3) => {
            elem$3.classList.add(cls$3);
          });
          var len = xs.a.length;
          var i = 0;
          if ((xs !== null)) {
            while ((i < len)) {
              var x0 = xs.a[i];
              f(x0);
              i = ((1 + i) | 0);
            }
            break matchResult5;
          } else if ((xs instanceof $ac_I)) {
            while ((i < len)) {
              var x0$1 = xs.a[i];
              f(x0$1);
              i = ((1 + i) | 0);
            }
            break matchResult5;
          } else if ((xs instanceof $ac_D)) {
            while ((i < len)) {
              var x0$2 = xs.a[i];
              f(x0$2);
              i = ((1 + i) | 0);
            }
            break matchResult5;
          } else if ((xs instanceof $ac_J)) {
            while ((i < len)) {
              var $x_1 = xs.a;
              var $x_2 = (i << 1);
              var x0$3_$_lo = $x_1[$x_2];
              var x0$3_$_hi = $x_1[(($x_2 + 1) | 0)];
              f($bL(x0$3_$_lo, x0$3_$_hi));
              i = ((1 + i) | 0);
            }
            break matchResult5;
          } else if ((xs instanceof $ac_F)) {
            while ((i < len)) {
              var x0$4 = xs.a[i];
              f(x0$4);
              i = ((1 + i) | 0);
            }
            break matchResult5;
          } else if ((xs instanceof $ac_C)) {
            while ((i < len)) {
              var x0$5 = xs.a[i];
              f($bC(x0$5));
              i = ((1 + i) | 0);
            }
            break matchResult5;
          } else if ((xs instanceof $ac_B)) {
            while ((i < len)) {
              var x0$6 = xs.a[i];
              f(x0$6);
              i = ((1 + i) | 0);
            }
            break matchResult5;
          } else if ((xs instanceof $ac_S)) {
            while ((i < len)) {
              var x0$7 = xs.a[i];
              f(x0$7);
              i = ((1 + i) | 0);
            }
            break matchResult5;
          } else if ((xs instanceof $ac_Z)) {
            while ((i < len)) {
              var x0$8 = xs.a[i];
              f(x0$8);
              i = ((1 + i) | 0);
            }
            break matchResult5;
          } else {
            throw new $c_s_MatchError(xs);
          }
        }
        elem$3.setAttribute(x18, x19);
        break matchResult5;
      }
      if ($is_sci_Seq(x$1$3)) {
        $p_Ldotty_tools_scaladoc_utils_HTML$Tag__unpackAttributes$1__Lorg_scalajs_dom_HTMLElement__sci_Seq__V($thiz, elem$3, x$1$3);
        break matchResult5;
      }
      throw new $c_s_MatchError(x$1$3);
    }
  })));
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_utils_HTML$Tag(elemFactory) {
  this.gH = null;
  this.gH = elemFactory;
}
$p = $c_Ldotty_tools_scaladoc_utils_HTML$Tag.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_utils_HTML$Tag;
/** @constructor */
function $h_Ldotty_tools_scaladoc_utils_HTML$Tag() {
}
$h_Ldotty_tools_scaladoc_utils_HTML$Tag.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().dr(this, 1955036683, true);
});
$p.p = (function(x$0) {
  if ((this === x$0)) {
    return true;
  } else if ((x$0 instanceof $c_Ldotty_tools_scaladoc_utils_HTML$Tag)) {
    var x = this.gH;
    var x$2 = x$0.gH;
    return ((x === null) ? (x$2 === null) : (x === x$2));
  } else {
    return false;
  }
});
$p.t = (function() {
  return $m_sr_ScalaRunTime$().dL(this);
});
$p.bE = (function() {
  return 1;
});
$p.bG = (function() {
  return "Tag";
});
$p.bF = (function(n) {
  if ((n === 0)) {
    return this.gH;
  }
  throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
});
$p.Z = (function(tags) {
  return this.D(new $c_sjsr_WrappedVarArgs([]), tags);
});
$p.cm = (function(first, rest) {
  return this.D(rest.bp(first), new $c_sjsr_WrappedVarArgs([]));
});
$p.D = (function(attrs, tags) {
  var elem = this.gH.a2();
  $p_Ldotty_tools_scaladoc_utils_HTML$Tag__unpackTags$1__Lorg_scalajs_dom_HTMLElement__sci_Seq__V(this, elem, tags);
  $p_Ldotty_tools_scaladoc_utils_HTML$Tag__unpackAttributes$1__Lorg_scalajs_dom_HTMLElement__sci_Seq__V(this, elem, attrs);
  return elem;
});
function $isArrayOf_Ldotty_tools_scaladoc_utils_HTML$Tag(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bl)));
}
var $d_Ldotty_tools_scaladoc_utils_HTML$Tag = new $TypeData().i($c_Ldotty_tools_scaladoc_utils_HTML$Tag, "dotty.tools.scaladoc.utils.HTML$Tag", ({
  bl: 1,
  d: 1,
  J: 1,
  a: 1
}));
function $isArrayOf_Ljava_io_IOException(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.dk)));
}
/** @constructor */
function $c_Ljava_io_OutputStream() {
}
$p = $c_Ljava_io_OutputStream.prototype = new $h_O();
$p.constructor = $c_Ljava_io_OutputStream;
/** @constructor */
function $h_Ljava_io_OutputStream() {
}
$h_Ljava_io_OutputStream.prototype = $p;
class $c_jl_AssertionError extends $c_jl_Error {
  constructor(detailMessage) {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, ("" + detailMessage), ((detailMessage instanceof $c_jl_Throwable) ? detailMessage : null), true, true);
  }
}
var $d_jl_AssertionError = new $TypeData().i($c_jl_AssertionError, "java.lang.AssertionError", ({
  dp: 1,
  aR: 1,
  r: 1,
  a: 1
}));
function $f_jl_Boolean__equals__O__Z($thiz, that) {
  return ($thiz === that);
}
function $f_jl_Boolean__hashCode__I($thiz) {
  return ($thiz ? 1231 : 1237);
}
function $f_jl_Boolean__toString__T($thiz) {
  return ("" + $thiz);
}
function $f_jl_Boolean__compareTo__O__I($thiz, o) {
  return (($thiz === o) ? 0 : ($thiz ? 1 : (-1)));
}
function $isArrayOf_jl_Boolean(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bo)));
}
var $d_jl_Boolean = new $TypeData().i(0, "java.lang.Boolean", ({
  bo: 1,
  a: 1,
  F: 1,
  a5: 1
}), ((x) => ((typeof x) === "boolean")));
function $f_jl_Character__hashCode__I($thiz) {
  return $thiz;
}
function $f_jl_Character__equals__O__Z($thiz, that) {
  return ((that instanceof $Char) && ($thiz === that.c));
}
function $f_jl_Character__toString__T($thiz) {
  return ("" + $cToS($thiz));
}
function $f_jl_Character__compareTo__O__I($thiz, o) {
  return (($thiz - o.c) | 0);
}
function $isArrayOf_jl_Character(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bq)));
}
var $d_jl_Character = new $TypeData().i(0, "java.lang.Character", ({
  bq: 1,
  a: 1,
  F: 1,
  a5: 1
}), ((x) => (x instanceof $Char)));
function $isArrayOf_jl_InterruptedException(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.dw)));
}
function $isArrayOf_jl_LinkageError(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.dz)));
}
function $ct_jl_RuntimeException__T__($thiz, s) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, s, null, true, true);
  return $thiz;
}
class $c_jl_RuntimeException extends $c_jl_Exception {
}
var $d_jl_RuntimeException = new $TypeData().i($c_jl_RuntimeException, "java.lang.RuntimeException", ({
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
function $ct_jl_StringBuilder__($thiz) {
  $thiz.w = "";
  return $thiz;
}
function $ct_jl_StringBuilder__T__($thiz, str) {
  $ct_jl_StringBuilder__($thiz);
  $thiz.w = str;
  return $thiz;
}
/** @constructor */
function $c_jl_StringBuilder() {
  this.w = null;
}
$p = $c_jl_StringBuilder.prototype = new $h_O();
$p.constructor = $c_jl_StringBuilder;
/** @constructor */
function $h_jl_StringBuilder() {
}
$h_jl_StringBuilder.prototype = $p;
$p.nO = (function(str) {
  var str$1 = $m_jl_String$().rz(str, 0, str.a.length);
  this.w = (("" + this.w) + str$1);
  return this;
});
$p.t = (function() {
  return this.w;
});
$p.l = (function() {
  return this.w.length;
});
$p.nV = (function(index) {
  return this.w.charCodeAt(index);
});
$p.nN = (function(csq) {
  this.w = (("" + this.w) + csq);
  return this;
});
var $d_jl_StringBuilder = new $TypeData().i($c_jl_StringBuilder, "java.lang.StringBuilder", ({
  dI: 1,
  aQ: 1,
  bn: 1,
  a: 1
}));
function $isArrayOf_jl_ThreadDeath(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.dL)));
}
class $c_jl_VirtualMachineError extends $c_jl_Error {
}
function $isArrayOf_jl_VirtualMachineError(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bA)));
}
function $isArrayOf_Ljava_math_BigDecimal(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.dR)));
}
function $isArrayOf_Ljava_math_BigInteger(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.dS)));
}
function $p_ju_Formatter__sendToDest__T__V($thiz, s) {
  if (($thiz.fb === null)) {
    $thiz.ev = (("" + $thiz.ev) + s);
  } else {
    $p_ju_Formatter__sendToDestSlowPath__O__V($thiz, [s]);
  }
}
function $p_ju_Formatter__sendToDest__T__T__V($thiz, s1, s2) {
  if (($thiz.fb === null)) {
    $thiz.ev = ((("" + $thiz.ev) + s1) + s2);
  } else {
    $p_ju_Formatter__sendToDestSlowPath__O__V($thiz, [s1, s2]);
  }
}
function $p_ju_Formatter__sendToDest__T__T__T__V($thiz, s1, s2, s3) {
  if (($thiz.fb === null)) {
    $thiz.ev = (($thiz.ev + (("" + s1) + s2)) + s3);
  } else {
    $p_ju_Formatter__sendToDestSlowPath__O__V($thiz, [s1, s2, s3]);
  }
}
function $p_ju_Formatter__sendToDestSlowPath__O__V($thiz, ss) {
  try {
    var len = (ss.length | 0);
    var i = 0;
    while ((i !== len)) {
      var t = ss[i];
      $thiz.fb.nN(t);
      i = ((1 + i) | 0);
    }
  } catch (e) {
    if ((!false)) {
      throw e;
    }
  }
}
function $p_ju_Formatter__format__ju_Formatter$LocaleInfo__T__AO__ju_Formatter($thiz, localeInfo, format, args) {
  if ($thiz.jp) {
    throw new $c_ju_FormatterClosedException();
  }
  var lastImplicitArgIndex = 0;
  var lastArgIndex = 0;
  var fmtLength = format.length;
  var fmtIndex = 0;
  while ((fmtIndex !== fmtLength)) {
    var fromIndex = fmtIndex;
    var nextPercentIndex = (format.indexOf("%", fromIndex) | 0);
    if ((nextPercentIndex < 0)) {
      var beginIndex = fmtIndex;
      $p_ju_Formatter__sendToDest__T__V($thiz, format.substring(beginIndex));
      return $thiz;
    }
    var beginIndex$1 = fmtIndex;
    $p_ju_Formatter__sendToDest__T__V($thiz, format.substring(beginIndex$1, nextPercentIndex));
    var formatSpecifierIndex = ((1 + nextPercentIndex) | 0);
    var re = $m_ju_Formatter$().lS;
    re.lastIndex = formatSpecifierIndex;
    var execResult = re.exec(format);
    if (((execResult === null) || ((execResult.index | 0) !== formatSpecifierIndex))) {
      $p_ju_Formatter__throwUnknownFormatConversionException__C__E($thiz, ((formatSpecifierIndex === fmtLength) ? 37 : format.charCodeAt(formatSpecifierIndex)));
    }
    fmtIndex = (re.lastIndex | 0);
    var index = ((fmtIndex - 1) | 0);
    var conversion$2 = format.charCodeAt(index);
    var flags = $p_ju_Formatter__parseFlags__T__C__I($thiz, execResult[2], conversion$2);
    var width = $p_ju_Formatter__parsePositiveInt__O__I($thiz, execResult[3]);
    var precision = $p_ju_Formatter__parsePositiveInt__O__I($thiz, execResult[4]);
    if ((width === (-2))) {
      $p_ju_Formatter__throwIllegalFormatWidthException__I__E($thiz, (-2147483648));
    }
    if ((precision === (-2))) {
      $p_ju_Formatter__throwIllegalFormatPrecisionException__I__E($thiz, (-2147483648));
    }
    if ((conversion$2 === 110)) {
      if ((precision !== (-1))) {
        $p_ju_Formatter__throwIllegalFormatPrecisionException__I__E($thiz, precision);
      }
      if ((width !== (-1))) {
        $p_ju_Formatter__throwIllegalFormatWidthException__I__E($thiz, width);
      }
      if ((flags !== 0)) {
        $thiz.kX(flags);
      }
      $p_ju_Formatter__sendToDest__T__V($thiz, "\n");
    } else if ((conversion$2 === 37)) {
      if ((precision !== (-1))) {
        $p_ju_Formatter__throwIllegalFormatPrecisionException__I__E($thiz, precision);
      }
      if ((((17 & flags) === 17) || ((12 & flags) === 12))) {
        $thiz.kX(flags);
      }
      if ((((1 & flags) !== 0) && (width === (-1)))) {
        $p_ju_Formatter__throwMissingFormatWidthException__T__E($thiz, $ps_ju_Formatter__fullFormatSpecifier$1__O__T(execResult));
      }
      if ((((-2) & flags) !== 0)) {
        $thiz.iY(37, flags, (-2));
      }
      $p_ju_Formatter__padAndSendToDestNoZeroPad__I__I__T__V($thiz, flags, width, "%");
    } else {
      var conversionLower = (((256 & flags) !== 0) ? (65535 & ((32 + conversion$2) | 0)) : conversion$2);
      var illegalFlags = $m_ju_Formatter$().lR.a[((conversionLower - 97) | 0)];
      if (((illegalFlags === (-1)) || (((256 & flags) & illegalFlags) !== 0))) {
        $p_ju_Formatter__throwUnknownFormatConversionException__C__E($thiz, conversion$2);
      }
      if ((((17 & flags) !== 0) && (width === (-1)))) {
        $p_ju_Formatter__throwMissingFormatWidthException__T__E($thiz, $ps_ju_Formatter__fullFormatSpecifier$1__O__T(execResult));
      }
      if ((((17 & flags) === 17) || ((12 & flags) === 12))) {
        $thiz.kX(flags);
      }
      if (((precision !== (-1)) && ((512 & illegalFlags) !== 0))) {
        $p_ju_Formatter__throwIllegalFormatPrecisionException__I__E($thiz, precision);
      }
      if (((flags & illegalFlags) !== 0)) {
        $thiz.iY(conversionLower, flags, illegalFlags);
      }
      if (((128 & flags) !== 0)) {
        var argIndex = lastArgIndex;
      } else {
        var i = $p_ju_Formatter__parsePositiveInt__O__I($thiz, execResult[1]);
        if ((i === (-1))) {
          lastImplicitArgIndex = ((1 + lastImplicitArgIndex) | 0);
          var argIndex = lastImplicitArgIndex;
        } else {
          if ((i <= 0)) {
            $p_ju_Formatter__throwIllegalFormatArgumentIndexException__I__E($thiz, i);
          }
          var argIndex = i;
        }
      }
      if (((argIndex <= 0) || (argIndex > args.a.length))) {
        $p_ju_Formatter__throwMissingFormatArgumentException__T__E($thiz, $ps_ju_Formatter__fullFormatSpecifier$1__O__T(execResult));
      }
      lastArgIndex = argIndex;
      var arg = args.a[((argIndex - 1) | 0)];
      if ((((arg === null) && (conversionLower !== 98)) && (conversionLower !== 115))) {
        $p_ju_Formatter__formatNonNumericString__ju_Formatter$LocaleInfo__I__I__I__T__V($thiz, $m_ju_Formatter$RootLocaleInfo$(), flags, width, precision, "null");
      } else {
        $p_ju_Formatter__formatArg__ju_Formatter$LocaleInfo__O__C__I__I__I__V($thiz, localeInfo, arg, conversionLower, flags, width, precision);
      }
    }
  }
  return $thiz;
}
function $p_ju_Formatter__parseFlags__T__C__I($thiz, flags, conversion) {
  var bits = (((((conversion - 65) | 0) >>> 0) <= 25) ? 256 : 0);
  var len = flags.length;
  var i = 0;
  while ((i !== len)) {
    var index = i;
    var f = flags.charCodeAt(index);
    switch (f) {
      case 45: {
        var bit = 1;
        break;
      }
      case 35: {
        var bit = 2;
        break;
      }
      case 43: {
        var bit = 4;
        break;
      }
      case 32: {
        var bit = 8;
        break;
      }
      case 48: {
        var bit = 16;
        break;
      }
      case 44: {
        var bit = 32;
        break;
      }
      case 40: {
        var bit = 64;
        break;
      }
      case 60: {
        var bit = 128;
        break;
      }
      default: {
        var bit;
        throw new $c_jl_AssertionError($bC(f));
      }
    }
    if (((bits & bit) !== 0)) {
      $p_ju_Formatter__throwDuplicateFormatFlagsException__C__E($thiz, f);
    }
    bits = (bits | bit);
    i = ((1 + i) | 0);
  }
  return bits;
}
function $p_ju_Formatter__parsePositiveInt__O__I($thiz, capture) {
  if ((capture !== (void 0))) {
    var x = (+parseInt(capture, 10));
    return ((x <= 2.147483647E9) ? $doubleToInt(x) : (-2));
  } else {
    return (-1);
  }
}
function $p_ju_Formatter__formatArg__ju_Formatter$LocaleInfo__O__C__I__I__I__V($thiz, localeInfo, arg, conversionLower, flags, width, precision) {
  switch (conversionLower) {
    case 98: {
      $p_ju_Formatter__formatNonNumericString__ju_Formatter$LocaleInfo__I__I__I__T__V($thiz, $m_ju_Formatter$RootLocaleInfo$(), flags, width, precision, (((arg === false) || (arg === null)) ? "false" : "true"));
      break;
    }
    case 104: {
      var $x_1 = $m_ju_Formatter$RootLocaleInfo$();
      var i = $dp_hashCode__I(arg);
      $p_ju_Formatter__formatNonNumericString__ju_Formatter$LocaleInfo__I__I__I__T__V($thiz, $x_1, flags, width, precision, (i >>> 0.0).toString(16));
      break;
    }
    case 115: {
      if ($is_ju_Formattable(arg)) {
        var formattableFlags = (((((1 & flags) !== 0) | 0) | (((2 & flags) !== 0) ? 4 : 0)) | (((256 & flags) !== 0) ? 2 : 0));
        arg.st($thiz, formattableFlags, width, precision);
      } else {
        if (((2 & flags) !== 0)) {
          $thiz.iY(conversionLower, flags, 2);
        }
        $p_ju_Formatter__formatNonNumericString__ju_Formatter$LocaleInfo__I__I__I__T__V($thiz, localeInfo, flags, width, precision, ("" + arg));
      }
      break;
    }
    case 99: {
      if ((arg instanceof $Char)) {
        var $x_2 = ("" + $cToS($uC(arg)));
      } else {
        if ((!$isInt(arg))) {
          $thiz.hL(conversionLower, arg);
        }
        var x3 = (arg | 0);
        if (((x3 >>> 0) > 1114111)) {
          $p_ju_Formatter__throwIllegalFormatCodePointException__I__E($thiz, x3);
        }
        var $x_2 = $m_jl_Character$().hW(x3);
      }
      $p_ju_Formatter__formatNonNumericString__ju_Formatter$LocaleInfo__I__I__I__T__V($thiz, localeInfo, flags, width, (-1), $x_2);
      break;
    }
    case 100: {
      if ($isInt(arg)) {
        var $x_3 = ("" + (arg | 0));
      } else if ((arg instanceof $Long)) {
        var $x_4 = $uJ(arg);
        var x3$2_$_lo = $x_4.l;
        var x3$2_$_hi = $x_4.h;
        var $x_3 = $m_RTLong$().lb(x3$2_$_lo, x3$2_$_hi);
      } else {
        if ((!false)) {
          $thiz.hL(conversionLower, arg);
        }
        var $x_3 = arg.t();
      }
      $p_ju_Formatter__formatNumericString__ju_Formatter$LocaleInfo__I__I__T__T__V($thiz, localeInfo, flags, width, $x_3, "");
      break;
    }
    case 111:
    case 120: {
      var isOctal = (conversionLower === 111);
      var prefix = (((2 & flags) === 0) ? "" : (isOctal ? "0" : (((256 & flags) !== 0) ? "0X" : "0x")));
      if (false) {
        var radix = (isOctal ? 8 : 16);
        $p_ju_Formatter__formatNumericString__ju_Formatter$LocaleInfo__I__I__T__T__V($thiz, $m_ju_Formatter$RootLocaleInfo$(), flags, width, arg.hW(radix), prefix);
      } else {
        if ($isInt(arg)) {
          var x2$5 = (arg | 0);
          var str$6 = (isOctal ? (x2$5 >>> 0.0).toString(8) : (x2$5 >>> 0.0).toString(16));
        } else {
          if ((!(arg instanceof $Long))) {
            $thiz.hL(conversionLower, arg);
          }
          var $x_5 = $uJ(arg);
          var x3$3_$_lo = $x_5.l;
          var x3$3_$_hi = $x_5.h;
          var str$6 = (isOctal ? $m_jl_Long$().rh(x3$3_$_lo, x3$3_$_hi) : $m_jl_Long$().ov(x3$3_$_lo, x3$3_$_hi));
        }
        if (((76 & flags) !== 0)) {
          $thiz.iY(conversionLower, flags, 76);
        }
        $p_ju_Formatter__padAndSendToDest__ju_Formatter$LocaleInfo__I__I__T__T__V($thiz, $m_ju_Formatter$RootLocaleInfo$(), flags, width, prefix, $p_ju_Formatter__applyNumberUpperCase__I__T__T($thiz, flags, str$6));
      }
      break;
    }
    case 101:
    case 102:
    case 103: {
      if (((typeof arg) === "number")) {
        var x2$6 = (+arg);
        if (((x2$6 !== x2$6) || ((x2$6 === Infinity) || (x2$6 === (-Infinity))))) {
          $p_ju_Formatter__formatNaNOrInfinite__I__I__D__V($thiz, flags, width, x2$6);
        } else {
          $p_ju_Formatter__formatDecimal$1__ju_Formatter$Decimal__I__I__C__ju_Formatter$LocaleInfo__I__V($thiz, $m_ju_Formatter$().rk(x2$6), flags, precision, conversionLower, localeInfo, width);
        }
      } else if (false) {
        $p_ju_Formatter__formatDecimal$1__ju_Formatter$Decimal__I__I__C__ju_Formatter$LocaleInfo__I__V($thiz, $m_ju_Formatter$().rj(arg), flags, precision, conversionLower, localeInfo, width);
      } else {
        $thiz.hL(conversionLower, arg);
      }
      break;
    }
    case 97: {
      if (((typeof arg) === "number")) {
        $p_ju_Formatter__formatHexFloatingPoint__I__I__I__D__V($thiz, flags, width, precision, (+arg));
      } else {
        $thiz.hL(conversionLower, arg);
      }
      break;
    }
    default: {
      throw new $c_jl_AssertionError((("Unknown conversion '" + $cToS(conversionLower)) + "' was not rejected earlier"));
    }
  }
}
function $p_ju_Formatter__flagsToString__I__T($thiz, flags) {
  return ((((((((((1 & flags) !== 0) ? "-" : "") + (((2 & flags) !== 0) ? "#" : "")) + (((4 & flags) !== 0) ? "+" : "")) + (((8 & flags) !== 0) ? " " : "")) + (((16 & flags) !== 0) ? "0" : "")) + (((32 & flags) !== 0) ? "," : "")) + (((64 & flags) !== 0) ? "(" : "")) + (((128 & flags) !== 0) ? "<" : ""));
}
function $p_ju_Formatter__computerizedScientificNotation__ju_Formatter$Decimal__I__Z__T($thiz, x, digitsAfterDot, forceDecimalSep) {
  var rounded = x.oK(((1 + digitsAfterDot) | 0));
  var signStr = (rounded.e0 ? "-" : "");
  var intStr = rounded.e1;
  var fractionalDigitCount = ((intStr.length - 1) | 0);
  var missingZeros = ((digitsAfterDot - fractionalDigitCount) | 0);
  var integerPart = intStr.substring(0, 1);
  var fractionalPart = (("" + intStr.substring(1)) + $m_ju_Formatter$().kW(missingZeros));
  var significandStr = (((fractionalPart === "") && (!forceDecimalSep)) ? integerPart : ((integerPart + ".") + fractionalPart));
  var exponent = ((fractionalDigitCount - rounded.dy) | 0);
  var exponentSign = ((exponent < 0) ? "-" : "+");
  var sign = (exponent >> 31);
  var this$5 = (((exponent ^ sign) - sign) | 0);
  var exponentAbsStr0 = ("" + this$5);
  var exponentAbsStr = ((exponentAbsStr0.length === 1) ? ("0" + exponentAbsStr0) : exponentAbsStr0);
  return ((((signStr + significandStr) + "e") + exponentSign) + exponentAbsStr);
}
function $p_ju_Formatter__decimalNotation__ju_Formatter$Decimal__I__Z__T($thiz, x, scale, forceDecimalSep) {
  var rounded = x.s2(scale);
  var signStr = (rounded.e0 ? "-" : "");
  var intStr = rounded.e1;
  var intStrLen = intStr.length;
  var minDigits = ((1 + scale) | 0);
  var expandedIntStr = ((intStrLen >= minDigits) ? intStr : (("" + $m_ju_Formatter$().kW(((minDigits - intStrLen) | 0))) + intStr));
  var dotPos = ((expandedIntStr.length - scale) | 0);
  var integerPart = (signStr + expandedIntStr.substring(0, dotPos));
  return (((scale === 0) && (!forceDecimalSep)) ? integerPart : ((integerPart + ".") + expandedIntStr.substring(dotPos)));
}
function $p_ju_Formatter__generalScientificNotation__ju_Formatter$Decimal__I__Z__T($thiz, x, precision, forceDecimalSep) {
  var p = ((precision === 0) ? 1 : precision);
  var rounded = x.oK(p);
  var orderOfMagnitude = ((((rounded.e1.length - 1) | 0) - rounded.dy) | 0);
  if (((orderOfMagnitude >= (-4)) && (orderOfMagnitude < p))) {
    var b = ((((p - orderOfMagnitude) | 0) - 1) | 0);
    return $p_ju_Formatter__decimalNotation__ju_Formatter$Decimal__I__Z__T($thiz, rounded, ((b < 0) ? 0 : b), forceDecimalSep);
  } else {
    return $p_ju_Formatter__computerizedScientificNotation__ju_Formatter$Decimal__I__Z__T($thiz, rounded, ((p - 1) | 0), forceDecimalSep);
  }
}
function $p_ju_Formatter__formatHexFloatingPoint__I__I__I__D__V($thiz, flags, width, precision, arg) {
  if (((arg !== arg) || ((arg === Infinity) || (arg === (-Infinity))))) {
    $p_ju_Formatter__formatNaNOrInfinite__I__I__D__V($thiz, flags, width, arg);
  } else {
    var fpBitsDataView = $fpBitsDataView;
    fpBitsDataView.setFloat64(0, arg, true);
    var lo = (fpBitsDataView.getInt32(0, true) | 0);
    var hi = (fpBitsDataView.getInt32(4, true) | 0);
    var negative = (hi < 0);
    var hi$1 = (1048575 & hi);
    var biasedExponent = (2047 & ((hi >>> 20) | 0));
    var actualPrecision = ((precision === 0) ? 1 : ((precision > 12) ? (-1) : precision));
    var signStr = (negative ? "-" : (((4 & flags) !== 0) ? "+" : (((8 & flags) !== 0) ? " " : "")));
    if ((biasedExponent === 0)) {
      if (((lo | hi$1) === 0)) {
        var x1___1 = "0";
        var x1___2 = $bL(0, 0);
        var x1___3 = 0;
      } else if ((actualPrecision === (-1))) {
        var x1___1 = "0";
        var x1___2 = $bL(lo, hi$1);
        var x1___3 = (-1022);
      } else {
        var leadingZeros = ((hi$1 !== 0) ? Math.clz32(hi$1) : ((32 + Math.clz32(lo)) | 0));
        var shift = ((leadingZeros - 11) | 0);
        var lo$2 = (((32 & shift) === 0) ? (lo << shift) : 0);
        var hi$2 = (((32 & shift) === 0) ? (((((lo >>> 1) | 0) >>> (~shift)) | 0) | (hi$1 << shift)) : (lo << shift));
        var hi$3 = (1048575 & hi$2);
        var normalizedExponent = (((-1022) - shift) | 0);
        var x1___1 = "1";
        var x1___2 = $bL(lo$2, hi$3);
        var x1___3 = normalizedExponent;
      }
    } else {
      var _3 = ((biasedExponent - 1023) | 0);
      var x1___1 = "1";
      var x1___2 = $bL(lo, hi$1);
      var x1___3 = _3;
    }
    var implicitBitStr = x1___1;
    var $x_1 = $uJ(x1___2);
    var mantissa_$_lo = $x_1.l;
    var mantissa_$_hi = $x_1.h;
    var exponent = (x1___3 | 0);
    if ((actualPrecision === (-1))) {
      var roundedMantissa_$_lo = mantissa_$_lo;
      var roundedMantissa_$_hi = mantissa_$_hi;
    } else {
      var n = ((52 - (actualPrecision << 2)) | 0);
      var lo$3 = (((32 & n) === 0) ? (1 << n) : 0);
      var hi$4 = (((32 & n) === 0) ? 0 : (1 << n));
      var lo$4 = ((lo$3 - 1) | 0);
      var hi$5 = ((((hi$4 - 1) | 0) + ((lo$4 !== (-1)) | 0)) | 0);
      var lo$5 = (((lo$3 >>> 1) | 0) | (hi$4 << 31));
      var hi$6 = (hi$4 >> 1);
      var lo$6 = (~lo$4);
      var hi$7 = (~hi$5);
      var lo$7 = (mantissa_$_lo & lo$6);
      var hi$8 = (mantissa_$_hi & hi$7);
      var lo$8 = (mantissa_$_lo & lo$4);
      var hi$9 = (mantissa_$_hi & hi$5);
      if (((hi$9 === hi$6) ? ((lo$8 >>> 0) < (lo$5 >>> 0)) : (hi$9 < hi$6))) {
        var roundedMantissa_$_lo = lo$7;
        var roundedMantissa_$_hi = hi$8;
      } else if (((hi$9 === hi$6) ? ((lo$8 >>> 0) > (lo$5 >>> 0)) : (hi$9 > hi$6))) {
        var lo$9 = ((lo$7 + lo$3) | 0);
        var hi$10 = ((((hi$8 + hi$4) | 0) + (((lo$9 >>> 0) < (lo$7 >>> 0)) | 0)) | 0);
        var roundedMantissa_$_lo = lo$9;
        var roundedMantissa_$_hi = hi$10;
      } else if ((((lo$7 & lo$3) | (hi$8 & hi$4)) === 0)) {
        var roundedMantissa_$_lo = lo$7;
        var roundedMantissa_$_hi = hi$8;
      } else {
        var lo$11 = ((lo$7 + lo$3) | 0);
        var hi$12 = ((((hi$8 + hi$4) | 0) + (((lo$11 >>> 0) < (lo$7 >>> 0)) | 0)) | 0);
        var roundedMantissa_$_lo = lo$11;
        var roundedMantissa_$_hi = hi$12;
      }
    }
    var baseStr = $m_jl_Long$().ov(roundedMantissa_$_lo, roundedMantissa_$_hi);
    var beginIndex = baseStr.length;
    var padded = (("" + "0000000000000".substring(beginIndex)) + baseStr);
    $m_ju_Formatter$();
    if ((!(padded.length === 13))) {
      throw new $c_jl_AssertionError("padded mantissa does not have the right number of bits");
    }
    var minLength = ((actualPrecision < 1) ? 1 : actualPrecision);
    var len = padded.length;
    while (((len > minLength) && (padded.charCodeAt(((len - 1) | 0)) === 48))) {
      len = ((len - 1) | 0);
    }
    var endIndex = len;
    var mantissaStr = padded.substring(0, endIndex);
    var exponentStr = ("" + exponent);
    $p_ju_Formatter__padAndSendToDest__ju_Formatter$LocaleInfo__I__I__T__T__V($thiz, $m_ju_Formatter$RootLocaleInfo$(), flags, width, (signStr + (((256 & flags) !== 0) ? "0X" : "0x")), $p_ju_Formatter__applyNumberUpperCase__I__T__T($thiz, flags, ((((implicitBitStr + ".") + mantissaStr) + "p") + exponentStr)));
  }
}
function $p_ju_Formatter__formatNonNumericString__ju_Formatter$LocaleInfo__I__I__I__T__V($thiz, localeInfo, flags, width, precision, str) {
  $p_ju_Formatter__padAndSendToDestNoZeroPad__I__I__T__V($thiz, flags, width, $p_ju_Formatter__applyUpperCase__ju_Formatter$LocaleInfo__I__T__T($thiz, localeInfo, flags, (((precision >>> 0) >= (str.length >>> 0)) ? str : str.substring(0, precision))));
}
function $p_ju_Formatter__formatNaNOrInfinite__I__I__D__V($thiz, flags, width, x) {
  $p_ju_Formatter__padAndSendToDestNoZeroPad__I__I__T__V($thiz, flags, width, $p_ju_Formatter__applyNumberUpperCase__I__T__T($thiz, flags, ((x !== x) ? "NaN" : ((x > 0.0) ? (((4 & flags) !== 0) ? "+Infinity" : (((8 & flags) !== 0) ? " Infinity" : "Infinity")) : (((64 & flags) !== 0) ? "(Infinity)" : "-Infinity")))));
}
function $p_ju_Formatter__formatNumericString__ju_Formatter$LocaleInfo__I__I__T__T__V($thiz, localeInfo, flags, width, str, basePrefix) {
  if (((str.length >= width) && ((110 & flags) === 0))) {
    $p_ju_Formatter__sendToDest__T__V($thiz, $p_ju_Formatter__applyNumberUpperCase__I__T__T($thiz, flags, str));
  } else if (((126 & flags) === 0)) {
    $p_ju_Formatter__padAndSendToDestNoZeroPad__I__I__T__V($thiz, flags, width, $p_ju_Formatter__applyNumberUpperCase__I__T__T($thiz, flags, str));
  } else {
    if ((str.charCodeAt(0) !== 45)) {
      if (((4 & flags) !== 0)) {
        var x1___1 = "+";
        var x1___2 = str;
      } else if (((8 & flags) !== 0)) {
        var x1___1 = " ";
        var x1___2 = str;
      } else {
        var x1___1 = "";
        var x1___2 = str;
      }
    } else if (((64 & flags) !== 0)) {
      var x1___1 = "(";
      var x1___2 = (str.substring(1) + ")");
    } else {
      var x1___1 = "-";
      var x1___2 = str.substring(1);
    }
    var numberPrefix = x1___1;
    var rest0 = x1___2;
    $p_ju_Formatter__padAndSendToDest__ju_Formatter$LocaleInfo__I__I__T__T__V($thiz, localeInfo, flags, width, (("" + numberPrefix) + basePrefix), $p_ju_Formatter__applyNumberUpperCase__I__T__T($thiz, flags, (((32 & flags) !== 0) ? $p_ju_Formatter__insertGroupingCommas__ju_Formatter$LocaleInfo__T__T($thiz, localeInfo, rest0) : rest0)));
  }
}
function $p_ju_Formatter__insertGroupingCommas__ju_Formatter$LocaleInfo__T__T($thiz, localeInfo, s) {
  var len = s.length;
  var index = 0;
  while (((index !== len) && ((((s.charCodeAt(index) - 48) | 0) >>> 0) <= 9))) {
    index = ((1 + index) | 0);
  }
  index = ((index - 3) | 0);
  if ((index <= 0)) {
    return s;
  } else {
    var beginIndex = index;
    var result = s.substring(beginIndex);
    while ((index > 3)) {
      var next = ((index - 3) | 0);
      var endIndex = index;
      result = ((s.substring(next, endIndex) + ",") + result);
      index = next;
    }
    var endIndex$1 = index;
    return ((s.substring(0, endIndex$1) + ",") + result);
  }
}
function $p_ju_Formatter__applyNumberUpperCase__I__T__T($thiz, flags, str) {
  return (((256 & flags) !== 0) ? str.toUpperCase() : str);
}
function $p_ju_Formatter__applyUpperCase__ju_Formatter$LocaleInfo__I__T__T($thiz, localeInfo, flags, str) {
  return (((256 & flags) !== 0) ? str.toUpperCase() : str);
}
function $p_ju_Formatter__padAndSendToDestNoZeroPad__I__I__T__V($thiz, flags, width, str) {
  var len = str.length;
  if ((len >= width)) {
    $p_ju_Formatter__sendToDest__T__V($thiz, str);
  } else if (((1 & flags) !== 0)) {
    $p_ju_Formatter__sendToDest__T__T__V($thiz, str, $p_ju_Formatter__strRepeat__T__I__T($thiz, " ", ((width - len) | 0)));
  } else {
    $p_ju_Formatter__sendToDest__T__T__V($thiz, $p_ju_Formatter__strRepeat__T__I__T($thiz, " ", ((width - len) | 0)), str);
  }
}
function $p_ju_Formatter__padAndSendToDest__ju_Formatter$LocaleInfo__I__I__T__T__V($thiz, localeInfo, flags, width, prefix, str) {
  var len = ((prefix.length + str.length) | 0);
  if ((len >= width)) {
    $p_ju_Formatter__sendToDest__T__T__V($thiz, prefix, str);
  } else if (((16 & flags) !== 0)) {
    $p_ju_Formatter__sendToDest__T__T__T__V($thiz, prefix, $p_ju_Formatter__strRepeat__T__I__T($thiz, "0", ((width - len) | 0)), str);
  } else if (((1 & flags) !== 0)) {
    $p_ju_Formatter__sendToDest__T__T__T__V($thiz, prefix, str, $p_ju_Formatter__strRepeat__T__I__T($thiz, " ", ((width - len) | 0)));
  } else {
    $p_ju_Formatter__sendToDest__T__T__T__V($thiz, $p_ju_Formatter__strRepeat__T__I__T($thiz, " ", ((width - len) | 0)), prefix, str);
  }
}
function $p_ju_Formatter__strRepeat__T__I__T($thiz, s, times) {
  var result = "";
  var i = 0;
  while ((i !== times)) {
    result = (("" + result) + s);
    i = ((1 + i) | 0);
  }
  return result;
}
function $p_ju_Formatter__throwDuplicateFormatFlagsException__C__E($thiz, flag) {
  throw new $c_ju_DuplicateFormatFlagsException(("" + $cToS(flag)));
}
function $p_ju_Formatter__throwUnknownFormatConversionException__C__E($thiz, conversion) {
  throw new $c_ju_UnknownFormatConversionException(("" + $cToS(conversion)));
}
function $p_ju_Formatter__throwIllegalFormatPrecisionException__I__E($thiz, precision) {
  throw new $c_ju_IllegalFormatPrecisionException(precision);
}
function $p_ju_Formatter__throwIllegalFormatWidthException__I__E($thiz, width) {
  throw new $c_ju_IllegalFormatWidthException(width);
}
function $p_ju_Formatter__throwIllegalFormatArgumentIndexException__I__E($thiz, index) {
  throw new $c_ju_IllegalFormatArgumentIndexException(((index === 0) ? "Illegal format argument index = 0" : "Format argument index: (not representable as int)"));
}
function $p_ju_Formatter__throwMissingFormatWidthException__T__E($thiz, fullFormatSpecifier) {
  throw new $c_ju_MissingFormatWidthException(fullFormatSpecifier);
}
function $p_ju_Formatter__throwMissingFormatArgumentException__T__E($thiz, fullFormatSpecifier) {
  throw new $c_ju_MissingFormatArgumentException(fullFormatSpecifier);
}
function $p_ju_Formatter__throwIllegalFormatCodePointException__I__E($thiz, arg) {
  throw new $c_ju_IllegalFormatCodePointException(arg);
}
function $ps_ju_Formatter__fullFormatSpecifier$1__O__T(execResult$1) {
  return ("%" + execResult$1[0]);
}
function $p_ju_Formatter__formatDecimal$1__ju_Formatter$Decimal__I__I__C__ju_Formatter$LocaleInfo__I__V($thiz, x, flags$1, precision$1, conversionLower$1, localeInfo$1, width$1) {
  var forceDecimalSep = ((2 & flags$1) !== 0);
  var actualPrecision = ((precision$1 >= 0) ? precision$1 : 6);
  switch (conversionLower$1) {
    case 101: {
      var $x_1 = $p_ju_Formatter__computerizedScientificNotation__ju_Formatter$Decimal__I__Z__T($thiz, x, actualPrecision, forceDecimalSep);
      break;
    }
    case 102: {
      var $x_1 = $p_ju_Formatter__decimalNotation__ju_Formatter$Decimal__I__Z__T($thiz, x, actualPrecision, forceDecimalSep);
      break;
    }
    default: {
      var $x_1 = $p_ju_Formatter__generalScientificNotation__ju_Formatter$Decimal__I__Z__T($thiz, x, actualPrecision, forceDecimalSep);
    }
  }
  $p_ju_Formatter__formatNumericString__ju_Formatter$LocaleInfo__I__I__T__T__V($thiz, localeInfo$1, flags$1, width$1, $x_1, "");
}
function $ct_ju_Formatter__jl_Appendable__ju_Formatter$LocaleInfo__($thiz, dest, formatterLocaleInfo) {
  $thiz.fb = dest;
  $thiz.lQ = formatterLocaleInfo;
  $thiz.ev = "";
  $thiz.jp = false;
  return $thiz;
}
function $ct_ju_Formatter__($thiz) {
  $ct_ju_Formatter__jl_Appendable__ju_Formatter$LocaleInfo__($thiz, null, $m_ju_Formatter$RootLocaleInfo$());
  return $thiz;
}
/** @constructor */
function $c_ju_Formatter() {
  this.fb = null;
  this.lQ = null;
  this.ev = null;
  this.jp = false;
}
$p = $c_ju_Formatter.prototype = new $h_O();
$p.constructor = $c_ju_Formatter;
/** @constructor */
function $h_ju_Formatter() {
}
$h_ju_Formatter.prototype = $p;
$p.qR = (function(format, args) {
  return $p_ju_Formatter__format__ju_Formatter$LocaleInfo__T__AO__ju_Formatter(this, this.lQ, format, args);
});
$p.t = (function() {
  if (this.jp) {
    throw new $c_ju_FormatterClosedException();
  }
  return ((this.fb === null) ? this.ev : this.fb.t());
});
$p.kX = (function(flags) {
  throw new $c_ju_IllegalFormatFlagsException($p_ju_Formatter__flagsToString__I__T(this, flags));
});
$p.iY = (function(conversionLower, flags, illegalFlags) {
  throw new $c_ju_FormatFlagsConversionMismatchException($p_ju_Formatter__flagsToString__I__T(this, (flags & illegalFlags)), conversionLower);
});
$p.hL = (function(conversionLower, arg) {
  throw new $c_ju_IllegalFormatConversionException(conversionLower, $objectGetClass(arg));
});
var $d_ju_Formatter = new $TypeData().i($c_ju_Formatter, "java.util.Formatter", ({
  dY: 1,
  aN: 1,
  aP: 1,
  aO: 1
}));
class $c_ju_concurrent_ExecutionException extends $c_jl_Exception {
  constructor(message, cause) {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, message, cause, true, true);
  }
}
var $d_ju_concurrent_ExecutionException = new $TypeData().i($c_ju_concurrent_ExecutionException, "java.util.concurrent.ExecutionException", ({
  ed: 1,
  v: 1,
  r: 1,
  a: 1
}));
/** @constructor */
function $c_ju_concurrent_TimeUnit() {
  this.dx = null;
  this.d8 = 0;
}
$p = $c_ju_concurrent_TimeUnit.prototype = new $h_jl_Enum();
$p.constructor = $c_ju_concurrent_TimeUnit;
/** @constructor */
function $h_ju_concurrent_TimeUnit() {
}
$h_ju_concurrent_TimeUnit.prototype = $p;
/** @constructor */
function $c_ju_internal_GenericArrayOps$ByteArrayOps$() {
}
$p = $c_ju_internal_GenericArrayOps$ByteArrayOps$.prototype = new $h_O();
$p.constructor = $c_ju_internal_GenericArrayOps$ByteArrayOps$;
/** @constructor */
function $h_ju_internal_GenericArrayOps$ByteArrayOps$() {
}
$h_ju_internal_GenericArrayOps$ByteArrayOps$.prototype = $p;
$p.ax = (function(o1, o2) {
  return (((o1 | 0) - (o2 | 0)) | 0);
});
$p.dt = (function(a, i, v) {
  var v$1 = (v | 0);
  a.a[i] = v$1;
});
$p.c0 = (function(a, i) {
  return a.a[i];
});
var $d_ju_internal_GenericArrayOps$ByteArrayOps$ = new $TypeData().i($c_ju_internal_GenericArrayOps$ByteArrayOps$, "java.util.internal.GenericArrayOps$ByteArrayOps$", ({
  em: 1,
  ar: 1,
  aA: 1,
  P: 1
}));
var $n_ju_internal_GenericArrayOps$ByteArrayOps$;
function $m_ju_internal_GenericArrayOps$ByteArrayOps$() {
  if ((!$n_ju_internal_GenericArrayOps$ByteArrayOps$)) {
    $n_ju_internal_GenericArrayOps$ByteArrayOps$ = new $c_ju_internal_GenericArrayOps$ByteArrayOps$();
  }
  return $n_ju_internal_GenericArrayOps$ByteArrayOps$;
}
/** @constructor */
function $c_ju_internal_GenericArrayOps$CharArrayOps$() {
}
$p = $c_ju_internal_GenericArrayOps$CharArrayOps$.prototype = new $h_O();
$p.constructor = $c_ju_internal_GenericArrayOps$CharArrayOps$;
/** @constructor */
function $h_ju_internal_GenericArrayOps$CharArrayOps$() {
}
$h_ju_internal_GenericArrayOps$CharArrayOps$.prototype = $p;
$p.ax = (function(o1, o2) {
  return (($uC(o1) - $uC(o2)) | 0);
});
$p.dt = (function(a, i, v) {
  var v$1 = $uC(v);
  a.a[i] = v$1;
});
$p.c0 = (function(a, i) {
  return $bC(a.a[i]);
});
var $d_ju_internal_GenericArrayOps$CharArrayOps$ = new $TypeData().i($c_ju_internal_GenericArrayOps$CharArrayOps$, "java.util.internal.GenericArrayOps$CharArrayOps$", ({
  en: 1,
  ar: 1,
  aA: 1,
  P: 1
}));
var $n_ju_internal_GenericArrayOps$CharArrayOps$;
function $m_ju_internal_GenericArrayOps$CharArrayOps$() {
  if ((!$n_ju_internal_GenericArrayOps$CharArrayOps$)) {
    $n_ju_internal_GenericArrayOps$CharArrayOps$ = new $c_ju_internal_GenericArrayOps$CharArrayOps$();
  }
  return $n_ju_internal_GenericArrayOps$CharArrayOps$;
}
/** @constructor */
function $c_ju_internal_GenericArrayOps$IntArrayOps$() {
}
$p = $c_ju_internal_GenericArrayOps$IntArrayOps$.prototype = new $h_O();
$p.constructor = $c_ju_internal_GenericArrayOps$IntArrayOps$;
/** @constructor */
function $h_ju_internal_GenericArrayOps$IntArrayOps$() {
}
$h_ju_internal_GenericArrayOps$IntArrayOps$.prototype = $p;
$p.ax = (function(o1, o2) {
  var x = (o1 | 0);
  var y = (o2 | 0);
  return ((x === y) ? 0 : ((x < y) ? (-1) : 1));
});
$p.dt = (function(a, i, v) {
  var v$1 = (v | 0);
  a.a[i] = v$1;
});
$p.c0 = (function(a, i) {
  return a.a[i];
});
var $d_ju_internal_GenericArrayOps$IntArrayOps$ = new $TypeData().i($c_ju_internal_GenericArrayOps$IntArrayOps$, "java.util.internal.GenericArrayOps$IntArrayOps$", ({
  eo: 1,
  ar: 1,
  aA: 1,
  P: 1
}));
var $n_ju_internal_GenericArrayOps$IntArrayOps$;
function $m_ju_internal_GenericArrayOps$IntArrayOps$() {
  if ((!$n_ju_internal_GenericArrayOps$IntArrayOps$)) {
    $n_ju_internal_GenericArrayOps$IntArrayOps$ = new $c_ju_internal_GenericArrayOps$IntArrayOps$();
  }
  return $n_ju_internal_GenericArrayOps$IntArrayOps$;
}
/** @constructor */
function $c_ju_internal_GenericArrayOps$LongArrayOps$() {
}
$p = $c_ju_internal_GenericArrayOps$LongArrayOps$.prototype = new $h_O();
$p.constructor = $c_ju_internal_GenericArrayOps$LongArrayOps$;
/** @constructor */
function $h_ju_internal_GenericArrayOps$LongArrayOps$() {
}
$h_ju_internal_GenericArrayOps$LongArrayOps$.prototype = $p;
$p.ax = (function(o1, o2) {
  var $x_1 = $uJ(o1);
  var x_$_lo = $x_1.l;
  var x_$_hi = $x_1.h;
  var $x_2 = $uJ(o2);
  var y_$_lo = $x_2.l;
  var y_$_hi = $x_2.h;
  return ((x_$_hi === y_$_hi) ? ((x_$_lo === y_$_lo) ? 0 : (((x_$_lo >>> 0) < (y_$_lo >>> 0)) ? (-1) : 1)) : ((x_$_hi < y_$_hi) ? (-1) : 1));
});
$p.dt = (function(a, i, v) {
  var $x_1 = $uJ(v);
  var v$1_$_lo = $x_1.l;
  var v$1_$_hi = $x_1.h;
  var $x_2 = a.a;
  var $x_3 = (i << 1);
  $x_2[$x_3] = v$1_$_lo;
  $x_2[(($x_3 + 1) | 0)] = v$1_$_hi;
});
$p.c0 = (function(a, i) {
  var $x_1 = a.a;
  var $x_2 = (i << 1);
  return $bL($x_1[$x_2], $x_1[(($x_2 + 1) | 0)]);
});
var $d_ju_internal_GenericArrayOps$LongArrayOps$ = new $TypeData().i($c_ju_internal_GenericArrayOps$LongArrayOps$, "java.util.internal.GenericArrayOps$LongArrayOps$", ({
  ep: 1,
  ar: 1,
  aA: 1,
  P: 1
}));
var $n_ju_internal_GenericArrayOps$LongArrayOps$;
function $m_ju_internal_GenericArrayOps$LongArrayOps$() {
  if ((!$n_ju_internal_GenericArrayOps$LongArrayOps$)) {
    $n_ju_internal_GenericArrayOps$LongArrayOps$ = new $c_ju_internal_GenericArrayOps$LongArrayOps$();
  }
  return $n_ju_internal_GenericArrayOps$LongArrayOps$;
}
/** @constructor */
function $c_ju_internal_GenericArrayOps$ShortArrayOps$() {
}
$p = $c_ju_internal_GenericArrayOps$ShortArrayOps$.prototype = new $h_O();
$p.constructor = $c_ju_internal_GenericArrayOps$ShortArrayOps$;
/** @constructor */
function $h_ju_internal_GenericArrayOps$ShortArrayOps$() {
}
$h_ju_internal_GenericArrayOps$ShortArrayOps$.prototype = $p;
$p.ax = (function(o1, o2) {
  return (((o1 | 0) - (o2 | 0)) | 0);
});
$p.dt = (function(a, i, v) {
  var v$1 = (v | 0);
  a.a[i] = v$1;
});
$p.c0 = (function(a, i) {
  return a.a[i];
});
var $d_ju_internal_GenericArrayOps$ShortArrayOps$ = new $TypeData().i($c_ju_internal_GenericArrayOps$ShortArrayOps$, "java.util.internal.GenericArrayOps$ShortArrayOps$", ({
  er: 1,
  ar: 1,
  aA: 1,
  P: 1
}));
var $n_ju_internal_GenericArrayOps$ShortArrayOps$;
function $m_ju_internal_GenericArrayOps$ShortArrayOps$() {
  if ((!$n_ju_internal_GenericArrayOps$ShortArrayOps$)) {
    $n_ju_internal_GenericArrayOps$ShortArrayOps$ = new $c_ju_internal_GenericArrayOps$ShortArrayOps$();
  }
  return $n_ju_internal_GenericArrayOps$ShortArrayOps$;
}
/** @constructor */
function $c_Lorg_scalajs_dom_DOMList$DOMListIterator(domList) {
  this.jz = null;
  this.gN = 0;
  this.jz = domList;
  this.gN = 0;
}
$p = $c_Lorg_scalajs_dom_DOMList$DOMListIterator.prototype = new $h_O();
$p.constructor = $c_Lorg_scalajs_dom_DOMList$DOMListIterator;
/** @constructor */
function $h_Lorg_scalajs_dom_DOMList$DOMListIterator() {
}
$h_Lorg_scalajs_dom_DOMList$DOMListIterator.prototype = $p;
$p.q = (function() {
  return (-1);
});
$p.d4 = (function(z, op) {
  return $f_sc_IterableOnceOps__foldLeft__O__F2__O(this, z, op);
});
$p.bc = (function(xs, start, len) {
  return $f_sc_IterableOnceOps__copyToArray__O__I__I__I(this, xs, start, len);
});
$p.cl = (function(b, start, sep, end) {
  return $f_sc_IterableOnceOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder(this, b, start, sep, end);
});
$p.c2 = (function() {
  $m_sci_List$();
  return $m_sci_Nil$().cc(this);
});
$p.c = (function() {
  return this;
});
$p.fB = (function(xs) {
  return $f_sc_Iterator__concat__F0__sc_Iterator(this, xs);
});
$p.cE = (function(n) {
  return $f_sc_Iterator__sliceIterator__I__I__sc_Iterator(this, n, (-1));
});
$p.t = (function() {
  return "<iterator>";
});
$p.k = (function() {
  return (this.gN < (this.jz.length | 0));
});
$p.f = (function() {
  var res = this.jz[this.gN];
  this.gN = ((1 + this.gN) | 0);
  return res;
});
var $d_Lorg_scalajs_dom_DOMList$DOMListIterator = new $TypeData().i($c_Lorg_scalajs_dom_DOMList$DOMListIterator, "org.scalajs.dom.DOMList$DOMListIterator", ({
  eA: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_s_PartialFunction$$anon$1() {
}
$p = $c_s_PartialFunction$$anon$1.prototype = new $h_O();
$p.constructor = $c_s_PartialFunction$$anon$1;
/** @constructor */
function $h_s_PartialFunction$$anon$1() {
}
$h_s_PartialFunction$$anon$1.prototype = $p;
$p.t = (function() {
  return "<function1>";
});
$p.f1 = (function(x$0) {
  this.iQ(x$0);
});
$p.bQ = (function(x, default$1) {
  return $f_s_PartialFunction__applyOrElse__O__F1__O(this, x, default$1);
});
$p.cF = (function(x) {
  return false;
});
$p.iQ = (function(x) {
  throw new $c_s_MatchError(x);
});
$p.d = (function(v1) {
  this.iQ(v1);
});
var $d_s_PartialFunction$$anon$1 = new $TypeData().i($c_s_PartialFunction$$anon$1, "scala.PartialFunction$$anon$1", ({
  eO: 1,
  i: 1,
  j: 1,
  a: 1
}));
/** @constructor */
function $c_s_PartialFunction$Lifted(pf) {
  this.mv = null;
  this.mv = pf;
}
$p = $c_s_PartialFunction$Lifted.prototype = new $h_sr_AbstractFunction1();
$p.constructor = $c_s_PartialFunction$Lifted;
/** @constructor */
function $h_s_PartialFunction$Lifted() {
}
$h_s_PartialFunction$Lifted.prototype = $p;
$p.hB = (function(x) {
  var z = this.mv.bQ(x, $m_s_PartialFunction$().gP);
  return ((!$m_s_PartialFunction$().oL(z)) ? new $c_s_Some(z) : $m_s_None$());
});
$p.d = (function(v1) {
  return this.hB(v1);
});
var $d_s_PartialFunction$Lifted = new $TypeData().i($c_s_PartialFunction$Lifted, "scala.PartialFunction$Lifted", ({
  eQ: 1,
  cW: 1,
  i: 1,
  a: 1
}));
/** @constructor */
function $c_sc_AbstractIterator() {
}
$p = $c_sc_AbstractIterator.prototype = new $h_O();
$p.constructor = $c_sc_AbstractIterator;
/** @constructor */
function $h_sc_AbstractIterator() {
}
$h_sc_AbstractIterator.prototype = $p;
$p.q = (function() {
  return (-1);
});
$p.d4 = (function(z, op) {
  return $f_sc_IterableOnceOps__foldLeft__O__F2__O(this, z, op);
});
$p.bc = (function(dest, start, n) {
  return $f_sc_IterableOnceOps__copyToArray__O__I__I__I(this, dest, start, n);
});
$p.cl = (function(b, start, sep, end) {
  return $f_sc_IterableOnceOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder(this, b, start, sep, end);
});
$p.c2 = (function() {
  $m_sci_List$();
  return $m_sci_Nil$().cc(this);
});
$p.c = (function() {
  return this;
});
$p.fB = (function(xs) {
  return $f_sc_Iterator__concat__F0__sc_Iterator(this, xs);
});
$p.cE = (function(n) {
  return this.gy(n, (-1));
});
$p.gy = (function(from, until) {
  return $f_sc_Iterator__sliceIterator__I__I__sc_Iterator(this, from, until);
});
$p.t = (function() {
  return "<iterator>";
});
/** @constructor */
function $c_sc_Map$() {
  this.g9 = null;
  this.mI = null;
  this.mJ = null;
  $ct_sc_MapFactory$Delegate__sc_MapFactory__(this, $m_sci_Map$());
  $n_sc_Map$ = this;
  this.mI = $ct_O__(new $c_O());
  this.mJ = new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => this.mI));
}
$p = $c_sc_Map$.prototype = new $h_sc_MapFactory$Delegate();
$p.constructor = $c_sc_Map$;
/** @constructor */
function $h_sc_Map$() {
}
$h_sc_Map$.prototype = $p;
var $d_sc_Map$ = new $TypeData().i($c_sc_Map$, "scala.collection.Map$", ({
  fj: 1,
  bR: 1,
  a: 1,
  aC: 1
}));
var $n_sc_Map$;
function $m_sc_Map$() {
  if ((!$n_sc_Map$)) {
    $n_sc_Map$ = new $c_sc_Map$();
  }
  return $n_sc_Map$;
}
function $ct_sc_SeqFactory$Delegate__sc_SeqFactory__($thiz, delegate) {
  $thiz.e5 = delegate;
  return $thiz;
}
/** @constructor */
function $c_sc_SeqFactory$Delegate() {
  this.e5 = null;
}
$p = $c_sc_SeqFactory$Delegate.prototype = new $h_O();
$p.constructor = $c_sc_SeqFactory$Delegate;
/** @constructor */
function $h_sc_SeqFactory$Delegate() {
}
$h_sc_SeqFactory$Delegate.prototype = $p;
$p.nT = (function(elems) {
  return this.e5.em(elems);
});
$p.fD = (function(it) {
  return this.e5.a3(it);
});
$p.T = (function() {
  return this.e5.T();
});
$p.em = (function(elems) {
  return this.nT(elems);
});
$p.a3 = (function(source) {
  return this.fD(source);
});
function $f_sc_SeqOps__distinct__O($thiz) {
  return $thiz.cb(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$2) => x$2)));
}
function $f_sc_SeqOps__distinctBy__F1__O($thiz, f) {
  return $thiz.en(new $c_sc_View$DistinctBy($thiz, f));
}
function $f_sc_SeqOps__isDefinedAt__I__Z($thiz, idx) {
  return ((idx >= 0) && ($thiz.aI(idx) > 0));
}
function $f_sc_SeqOps__indexOf__O__I__I($thiz, elem, from) {
  return $thiz.dU(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$2$2) => $m_sr_BoxesRunTime$().n(elem, _$2$2))), from);
}
function $f_sc_SeqOps__contains__O__Z($thiz, elem) {
  return $thiz.fC(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$4$2) => $m_sr_BoxesRunTime$().n(_$4$2, elem))));
}
function $f_sc_SeqOps__sorted__s_math_Ordering__O($thiz, ord) {
  var len = $thiz.l();
  var b = $thiz.cI();
  if ((len === 1)) {
    b.aq($thiz.m());
  } else if ((len > 1)) {
    b.aZ(len);
    var arr = new $ac_O(len);
    $thiz.bc(arr, 0, 2147483647);
    $m_ju_Arrays$().gz(arr, ord);
    var i = 0;
    while ((i < len)) {
      b.aq(arr.a[i]);
      i = ((1 + i) | 0);
    }
  }
  return b.aU();
}
function $f_sc_SeqOps__sortBy__F1__s_math_Ordering__O($thiz, f, ord) {
  return $thiz.bH(new $c_s_math_Ordering$$anon$1(f, ord));
}
function $f_sc_SeqOps__isEmpty__Z($thiz) {
  return ($thiz.aI(0) === 0);
}
function $f_sc_SeqOps__sameElements__sc_IterableOnce__Z($thiz, that) {
  var thisKnownSize = $thiz.q();
  if ((thisKnownSize !== (-1))) {
    var thatKnownSize = that.q();
    if ((thatKnownSize !== (-1))) {
      if ((thisKnownSize !== thatKnownSize)) {
        return false;
      }
      if ((thisKnownSize === 0)) {
        return true;
      }
    }
  }
  return $f_sc_Iterator__sameElements__sc_IterableOnce__Z($thiz.c(), that);
}
function $f_sc_StrictOptimizedIterableOps__map__F1__O($thiz, f) {
  var b = $thiz.aH().T();
  var it = $thiz.c();
  while (it.k()) {
    b.aq(f.d(it.f()));
  }
  return b.aU();
}
/** @constructor */
function $c_sci_Iterable$() {
  this.gT = null;
  $ct_sc_IterableFactory$Delegate__sc_IterableFactory__(this, $m_sci_List$());
}
$p = $c_sci_Iterable$.prototype = new $h_sc_IterableFactory$Delegate();
$p.constructor = $c_sci_Iterable$;
/** @constructor */
function $h_sci_Iterable$() {
}
$h_sci_Iterable$.prototype = $p;
$p.qW = (function(it) {
  return ($is_sci_Iterable(it) ? it : $c_sc_IterableFactory$Delegate.prototype.a3.call(this, it));
});
$p.a3 = (function(it) {
  return this.qW(it);
});
var $d_sci_Iterable$ = new $TypeData().i($c_sci_Iterable$, "scala.collection.immutable.Iterable$", ({
  fM: 1,
  bP: 1,
  a: 1,
  B: 1
}));
var $n_sci_Iterable$;
function $m_sci_Iterable$() {
  if ((!$n_sci_Iterable$)) {
    $n_sci_Iterable$ = new $c_sci_Iterable$();
  }
  return $n_sci_Iterable$;
}
/** @constructor */
function $c_sci_LazyList$() {
  this.ah = null;
  this.pi = null;
  $n_sci_LazyList$ = this;
  this.ah = $ct_sci_LazyList__O__(new $c_sci_LazyList(), $m_sci_LazyList$EmptyMarker$());
  this.pi = new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((_$9$2) => $m_sr_Statics$PFMarker$()));
}
$p = $c_sci_LazyList$.prototype = new $h_O();
$p.constructor = $c_sci_LazyList$;
/** @constructor */
function $h_sci_LazyList$() {
}
$h_sci_LazyList$.prototype = $p;
$p.em = (function(elems) {
  return this.kJ(elems);
});
$p.rU = (function(ll, n) {
  return $ct_sci_LazyList__O__(new $c_sci_LazyList(), new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c(((restRef, iRef) => (() => {
    var rest = restRef.iG;
    var i = iRef.iD;
    while (((i > 0) && (!($p_sci_LazyList__evaluated__sci_LazyList(rest) === $m_sci_LazyList$().ah)))) {
      rest = rest.bI();
      restRef.iG = rest;
      i = ((i - 1) | 0);
      iRef.iD = i;
    }
    return rest;
  }))(new $c_sr_ObjectRef(ll), new $c_sr_IntRef(n))));
});
$p.kJ = (function(coll) {
  return ((coll instanceof $c_sci_LazyList) ? coll : ((coll.q() === 0) ? this.ah : $ct_sci_LazyList__O__(new $c_sci_LazyList(), new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => this.oN(coll.c()))))));
});
$p.oO = (function(it, suffix) {
  return (it.k() ? $ct_sci_LazyList__O__sci_LazyList__(new $c_sci_LazyList(), it.f(), $ct_sci_LazyList__O__(new $c_sci_LazyList(), new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => this.oO(it, suffix))))) : suffix.a2());
});
$p.oN = (function(it) {
  return (it.k() ? $ct_sci_LazyList__O__sci_LazyList__(new $c_sci_LazyList(), it.f(), $ct_sci_LazyList__O__(new $c_sci_LazyList(), new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => this.oN(it))))) : this.ah);
});
$p.T = (function() {
  return new $c_sci_LazyList$LazyBuilder();
});
$p.a3 = (function(source) {
  return this.kJ(source);
});
var $d_sci_LazyList$ = new $TypeData().i($c_sci_LazyList$, "scala.collection.immutable.LazyList$", ({
  fN: 1,
  a: 1,
  B: 1,
  W: 1
}));
var $n_sci_LazyList$;
function $m_sci_LazyList$() {
  if ((!$n_sci_LazyList$)) {
    $n_sci_LazyList$ = new $c_sci_LazyList$();
  }
  return $n_sci_LazyList$;
}
/** @constructor */
function $c_scm_Builder$$anon$1(f$2, outer) {
  this.n8 = null;
  this.ha = null;
  this.n8 = f$2;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.ha = outer;
}
$p = $c_scm_Builder$$anon$1.prototype = new $h_O();
$p.constructor = $c_scm_Builder$$anon$1;
/** @constructor */
function $h_scm_Builder$$anon$1() {
}
$h_scm_Builder$$anon$1.prototype = $p;
$p.pU = (function(x) {
  this.ha.aq(x);
  return this;
});
$p.pF = (function(xs) {
  this.ha.b7(xs);
  return this;
});
$p.aZ = (function(size) {
  this.ha.aZ(size);
});
$p.aU = (function() {
  return this.n8.d(this.ha.aU());
});
$p.aq = (function(elem) {
  return this.pU(elem);
});
$p.b7 = (function(elems) {
  return this.pF(elems);
});
var $d_scm_Builder$$anon$1 = new $TypeData().i($c_scm_Builder$$anon$1, "scala.collection.mutable.Builder$$anon$1", ({
  gR: 1,
  H: 1,
  I: 1,
  K: 1
}));
function $ct_scm_GrowableBuilder__scm_Growable__($thiz, elems) {
  $thiz.dJ = elems;
  return $thiz;
}
/** @constructor */
function $c_scm_GrowableBuilder() {
  this.dJ = null;
}
$p = $c_scm_GrowableBuilder.prototype = new $h_O();
$p.constructor = $c_scm_GrowableBuilder;
/** @constructor */
function $h_scm_GrowableBuilder() {
}
$h_scm_GrowableBuilder.prototype = $p;
$p.aZ = (function(size) {
});
$p.pV = (function(elem) {
  this.dJ.aq(elem);
  return this;
});
$p.pG = (function(xs) {
  this.dJ.b7(xs);
  return this;
});
$p.aU = (function() {
  return this.dJ;
});
$p.aq = (function(elem) {
  return this.pV(elem);
});
$p.b7 = (function(elems) {
  return this.pG(elems);
});
var $d_scm_GrowableBuilder = new $TypeData().i($c_scm_GrowableBuilder, "scala.collection.mutable.GrowableBuilder", ({
  aI: 1,
  H: 1,
  I: 1,
  K: 1
}));
/** @constructor */
function $c_scm_Iterable$() {
  this.gT = null;
  $ct_sc_IterableFactory$Delegate__sc_IterableFactory__(this, $m_scm_ArrayBuffer$());
}
$p = $c_scm_Iterable$.prototype = new $h_sc_IterableFactory$Delegate();
$p.constructor = $c_scm_Iterable$;
/** @constructor */
function $h_scm_Iterable$() {
}
$h_scm_Iterable$.prototype = $p;
var $d_scm_Iterable$ = new $TypeData().i($c_scm_Iterable$, "scala.collection.mutable.Iterable$", ({
  ha: 1,
  bP: 1,
  a: 1,
  B: 1
}));
var $n_scm_Iterable$;
function $m_scm_Iterable$() {
  if ((!$n_scm_Iterable$)) {
    $n_scm_Iterable$ = new $c_scm_Iterable$();
  }
  return $n_scm_Iterable$;
}
/** @constructor */
function $c_scm_Map$() {
  this.g9 = null;
  $ct_sc_MapFactory$Delegate__sc_MapFactory__(this, $m_scm_HashMap$());
}
$p = $c_scm_Map$.prototype = new $h_sc_MapFactory$Delegate();
$p.constructor = $c_scm_Map$;
/** @constructor */
function $h_scm_Map$() {
}
$h_scm_Map$.prototype = $p;
var $d_scm_Map$ = new $TypeData().i($c_scm_Map$, "scala.collection.mutable.Map$", ({
  he: 1,
  bR: 1,
  a: 1,
  aC: 1
}));
var $n_scm_Map$;
function $m_scm_Map$() {
  if ((!$n_scm_Map$)) {
    $n_scm_Map$ = new $c_scm_Map$();
  }
  return $n_scm_Map$;
}
class $c_s_concurrent_Future$$anon$4 extends $c_jl_Throwable {
  constructor() {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  gq() {
    return $f_s_util_control_NoStackTrace__fillInStackTrace__jl_Throwable(this);
  }
}
var $d_s_concurrent_Future$$anon$4 = new $TypeData().i($c_s_concurrent_Future$$anon$4, "scala.concurrent.Future$$anon$4", ({
  hu: 1,
  r: 1,
  a: 1,
  aM: 1
}));
/** @constructor */
function $c_s_concurrent_duration_Duration() {
}
$p = $c_s_concurrent_duration_Duration.prototype = new $h_O();
$p.constructor = $c_s_concurrent_duration_Duration;
/** @constructor */
function $h_s_concurrent_duration_Duration() {
}
$h_s_concurrent_duration_Duration.prototype = $p;
$p.ks = (function(that) {
  return this.kr(that);
});
function $isArrayOf_sr_NonLocalReturnControl(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.iz)));
}
/** @constructor */
function $c_sjs_concurrent_QueueExecutionContext$PromisesExecutionContext() {
  this.nz = null;
  this.nz = Promise.resolve((void 0));
}
$p = $c_sjs_concurrent_QueueExecutionContext$PromisesExecutionContext.prototype = new $h_O();
$p.constructor = $c_sjs_concurrent_QueueExecutionContext$PromisesExecutionContext;
/** @constructor */
function $h_sjs_concurrent_QueueExecutionContext$PromisesExecutionContext() {
}
$h_sjs_concurrent_QueueExecutionContext$PromisesExecutionContext.prototype = $p;
$p.kB = (function(runnable) {
  this.nz.then(((arg1$2) => {
    try {
      runnable.fJ();
    } catch (e) {
      ((e instanceof $c_jl_Throwable) ? e : new $c_sjs_js_JavaScriptException(e)).hR($m_jl_System$Streams$().g3);
    }
  }));
});
$p.l2 = (function(t) {
  t.hR($m_jl_System$Streams$().g3);
});
var $d_sjs_concurrent_QueueExecutionContext$PromisesExecutionContext = new $TypeData().i($c_sjs_concurrent_QueueExecutionContext$PromisesExecutionContext, "scala.scalajs.concurrent.QueueExecutionContext$PromisesExecutionContext", ({
  iP: 1,
  b9: 1,
  b8: 1,
  aT: 1
}));
/** @constructor */
function $c_sjs_concurrent_QueueExecutionContext$TimeoutsExecutionContext() {
}
$p = $c_sjs_concurrent_QueueExecutionContext$TimeoutsExecutionContext.prototype = new $h_O();
$p.constructor = $c_sjs_concurrent_QueueExecutionContext$TimeoutsExecutionContext;
/** @constructor */
function $h_sjs_concurrent_QueueExecutionContext$TimeoutsExecutionContext() {
}
$h_sjs_concurrent_QueueExecutionContext$TimeoutsExecutionContext.prototype = $p;
$p.kB = (function(runnable) {
  setTimeout($m_sjs_js_Any$().r0(new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => {
    try {
      runnable.fJ();
    } catch (e) {
      ((e instanceof $c_jl_Throwable) ? e : new $c_sjs_js_JavaScriptException(e)).hR($m_jl_System$Streams$().g3);
    }
  }))), 0);
});
$p.l2 = (function(t) {
  t.hR($m_jl_System$Streams$().g3);
});
var $d_sjs_concurrent_QueueExecutionContext$TimeoutsExecutionContext = new $TypeData().i($c_sjs_concurrent_QueueExecutionContext$TimeoutsExecutionContext, "scala.scalajs.concurrent.QueueExecutionContext$TimeoutsExecutionContext", ({
  iQ: 1,
  b9: 1,
  b8: 1,
  aT: 1
}));
/** @constructor */
function $c_sjs_js_WrappedDictionary$DictionaryIterator(dict) {
  this.nB = null;
  this.kg = null;
  this.hr = 0;
  this.nB = dict;
  this.kg = Object.keys(dict);
  this.hr = 0;
}
$p = $c_sjs_js_WrappedDictionary$DictionaryIterator.prototype = new $h_O();
$p.constructor = $c_sjs_js_WrappedDictionary$DictionaryIterator;
/** @constructor */
function $h_sjs_js_WrappedDictionary$DictionaryIterator() {
}
$h_sjs_js_WrappedDictionary$DictionaryIterator.prototype = $p;
$p.c = (function() {
  return this;
});
$p.fB = (function(xs) {
  return $f_sc_Iterator__concat__F0__sc_Iterator(this, xs);
});
$p.cE = (function(n) {
  return $f_sc_Iterator__sliceIterator__I__I__sc_Iterator(this, n, (-1));
});
$p.t = (function() {
  return "<iterator>";
});
$p.d4 = (function(z, op) {
  return $f_sc_IterableOnceOps__foldLeft__O__F2__O(this, z, op);
});
$p.bc = (function(dest, start, n) {
  return $f_sc_IterableOnceOps__copyToArray__O__I__I__I(this, dest, start, n);
});
$p.cl = (function(b, start, sep, end) {
  return $f_sc_IterableOnceOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder(this, b, start, sep, end);
});
$p.c2 = (function() {
  $m_sci_List$();
  return $m_sci_Nil$().cc(this);
});
$p.q = (function() {
  return (-1);
});
$p.k = (function() {
  return (this.hr < (this.kg.length | 0));
});
$p.gw = (function() {
  var key = this.kg[this.hr];
  this.hr = ((1 + this.hr) | 0);
  var dict = this.nB;
  if ((!(!$m_sjs_js_WrappedDictionary$Cache$().nA.call(dict, key)))) {
    var $x_1 = dict[key];
  } else {
    var $x_1;
    throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), ("key not found: " + key));
  }
  return $ct_T2__O__O__(new $c_T2(), key, $x_1);
});
$p.f = (function() {
  return this.gw();
});
var $d_sjs_js_WrappedDictionary$DictionaryIterator = new $TypeData().i($c_sjs_js_WrappedDictionary$DictionaryIterator, "scala.scalajs.js.WrappedDictionary$DictionaryIterator", ({
  j1: 1,
  m: 1,
  b: 1,
  c: 1
}));
function $isArrayOf_s_util_CommandLineParser$ParseError(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.j9)));
}
/** @constructor */
function $c_s_util_Try() {
}
$p = $c_s_util_Try.prototype = new $h_O();
$p.constructor = $c_s_util_Try;
/** @constructor */
function $h_s_util_Try() {
}
$h_s_util_Try.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
function $isArrayOf_s_util_Try(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bd)));
}
/** @constructor */
function $c_Ldotty_tools_scaladoc_CodeSnippets$$anon$1() {
}
$p = $c_Ldotty_tools_scaladoc_CodeSnippets$$anon$1.prototype = new $h_sr_AbstractPartialFunction();
$p.constructor = $c_Ldotty_tools_scaladoc_CodeSnippets$$anon$1;
/** @constructor */
function $h_Ldotty_tools_scaladoc_CodeSnippets$$anon$1() {
}
$h_Ldotty_tools_scaladoc_CodeSnippets$$anon$1.prototype = $p;
$p.rf = (function(x) {
  return ((!(!(x instanceof HTMLElement))) || false);
});
$p.q5 = (function(x, default$1) {
  return ((!(!(x instanceof HTMLElement))) ? x : default$1.d(x));
});
$p.cF = (function(x) {
  return this.rf(x);
});
$p.bQ = (function(x, default$1) {
  return this.q5(x, default$1);
});
var $d_Ldotty_tools_scaladoc_CodeSnippets$$anon$1 = new $TypeData().i($c_Ldotty_tools_scaladoc_CodeSnippets$$anon$1, "dotty.tools.scaladoc.CodeSnippets$$anon$1", ({
  d3: 1,
  bc: 1,
  i: 1,
  j: 1,
  a: 1
}));
/** @constructor */
function $c_Ldotty_tools_scaladoc_NameAndKindQuery(name, kind) {
  this.gG = null;
  this.gF = null;
  this.gG = name;
  this.gF = kind;
}
$p = $c_Ldotty_tools_scaladoc_NameAndKindQuery.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_NameAndKindQuery;
/** @constructor */
function $h_Ldotty_tools_scaladoc_NameAndKindQuery() {
}
$h_Ldotty_tools_scaladoc_NameAndKindQuery.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().dr(this, 1848543406, true);
});
$p.p = (function(x$0) {
  if ((this === x$0)) {
    return true;
  } else if ((x$0 instanceof $c_Ldotty_tools_scaladoc_NameAndKindQuery)) {
    var x = this.gG;
    var x$2 = x$0.gG;
    if (((x === null) ? (x$2 === null) : x.p(x$2))) {
      var x$3 = this.gF;
      var x$4 = x$0.gF;
      return ((x$3 === null) ? (x$4 === null) : x$3.p(x$4));
    } else {
      return false;
    }
  } else {
    return false;
  }
});
$p.t = (function() {
  return $m_sr_ScalaRunTime$().dL(this);
});
$p.bE = (function() {
  return 2;
});
$p.bG = (function() {
  return "NameAndKindQuery";
});
$p.bF = (function(n) {
  if ((n === 0)) {
    return this.gG;
  }
  if ((n === 1)) {
    return this.gF;
  }
  throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
});
function $isArrayOf_Ldotty_tools_scaladoc_NameAndKindQuery(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bh)));
}
var $d_Ldotty_tools_scaladoc_NameAndKindQuery = new $TypeData().i($c_Ldotty_tools_scaladoc_NameAndKindQuery, "dotty.tools.scaladoc.NameAndKindQuery", ({
  bh: 1,
  be: 1,
  d: 1,
  J: 1,
  a: 1
}));
/** @constructor */
function $c_Ldotty_tools_scaladoc_SignatureQuery(signature) {
  this.i1 = null;
  this.i1 = signature;
}
$p = $c_Ldotty_tools_scaladoc_SignatureQuery.prototype = new $h_O();
$p.constructor = $c_Ldotty_tools_scaladoc_SignatureQuery;
/** @constructor */
function $h_Ldotty_tools_scaladoc_SignatureQuery() {
}
$h_Ldotty_tools_scaladoc_SignatureQuery.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().dr(this, 2012484474, true);
});
$p.p = (function(x$0) {
  return ((this === x$0) || ((x$0 instanceof $c_Ldotty_tools_scaladoc_SignatureQuery) && (this.i1 === x$0.i1)));
});
$p.t = (function() {
  return $m_sr_ScalaRunTime$().dL(this);
});
$p.bE = (function() {
  return 1;
});
$p.bG = (function() {
  return "SignatureQuery";
});
$p.bF = (function(n) {
  if ((n === 0)) {
    return this.i1;
  }
  throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
});
function $isArrayOf_Ldotty_tools_scaladoc_SignatureQuery(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bj)));
}
var $d_Ldotty_tools_scaladoc_SignatureQuery = new $TypeData().i($c_Ldotty_tools_scaladoc_SignatureQuery, "dotty.tools.scaladoc.SignatureQuery", ({
  bj: 1,
  be: 1,
  d: 1,
  J: 1,
  a: 1
}));
function $ct_Ljava_io_FilterOutputStream__Ljava_io_OutputStream__($thiz, out) {
  return $thiz;
}
/** @constructor */
function $c_Ljava_io_FilterOutputStream() {
}
$p = $c_Ljava_io_FilterOutputStream.prototype = new $h_Ljava_io_OutputStream();
$p.constructor = $c_Ljava_io_FilterOutputStream;
/** @constructor */
function $h_Ljava_io_FilterOutputStream() {
}
$h_Ljava_io_FilterOutputStream.prototype = $p;
class $c_jl_ArithmeticException extends $c_jl_RuntimeException {
  constructor(s) {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, s, null, true, true);
  }
}
var $d_jl_ArithmeticException = new $TypeData().i($c_jl_ArithmeticException, "java.lang.ArithmeticException", ({
  dm: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
function $f_jl_Byte__equals__O__Z($thiz, that) {
  return Object.is($thiz, that);
}
function $f_jl_Byte__hashCode__I($thiz) {
  return $thiz;
}
function $f_jl_Byte__toString__T($thiz) {
  return ("" + $thiz);
}
function $f_jl_Byte__compareTo__O__I($thiz, o) {
  return (($thiz - o) | 0);
}
function $isArrayOf_jl_Byte(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bp)));
}
var $d_jl_Byte = new $TypeData().i(0, "java.lang.Byte", ({
  bp: 1,
  ai: 1,
  a: 1,
  F: 1,
  a5: 1
}), ((x) => $isByte(x)));
function $isArrayOf_jl_ClassCastException(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.dt)));
}
function $ct_jl_IllegalArgumentException__T__($thiz, s) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, s, null, true, true);
  return $thiz;
}
function $ct_jl_IllegalArgumentException__($thiz) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, null, null, true, true);
  return $thiz;
}
class $c_jl_IllegalArgumentException extends $c_jl_RuntimeException {
}
var $d_jl_IllegalArgumentException = new $TypeData().i($c_jl_IllegalArgumentException, "java.lang.IllegalArgumentException", ({
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
function $ct_jl_IllegalStateException__T__($thiz, s) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, s, null, true, true);
  return $thiz;
}
class $c_jl_IllegalStateException extends $c_jl_RuntimeException {
}
var $d_jl_IllegalStateException = new $TypeData().i($c_jl_IllegalStateException, "java.lang.IllegalStateException", ({
  bt: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
function $ct_jl_IndexOutOfBoundsException__T__($thiz, s) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, s, null, true, true);
  return $thiz;
}
function $ct_jl_IndexOutOfBoundsException__I__($thiz, index) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, ("Index out of range: " + index), null, true, true);
  return $thiz;
}
class $c_jl_IndexOutOfBoundsException extends $c_jl_RuntimeException {
}
var $d_jl_IndexOutOfBoundsException = new $TypeData().i($c_jl_IndexOutOfBoundsException, "java.lang.IndexOutOfBoundsException", ({
  bu: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
/** @constructor */
function $c_jl_JSConsoleBasedPrintStream$DummyOutputStream() {
}
$p = $c_jl_JSConsoleBasedPrintStream$DummyOutputStream.prototype = new $h_Ljava_io_OutputStream();
$p.constructor = $c_jl_JSConsoleBasedPrintStream$DummyOutputStream;
/** @constructor */
function $h_jl_JSConsoleBasedPrintStream$DummyOutputStream() {
}
$h_jl_JSConsoleBasedPrintStream$DummyOutputStream.prototype = $p;
var $d_jl_JSConsoleBasedPrintStream$DummyOutputStream = new $TypeData().i($c_jl_JSConsoleBasedPrintStream$DummyOutputStream, "java.lang.JSConsoleBasedPrintStream$DummyOutputStream", ({
  dy: 1,
  bm: 1,
  aN: 1,
  aP: 1,
  aO: 1
}));
function $ct_jl_NullPointerException__T__($thiz, s) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, s, null, true, true);
  return $thiz;
}
function $ct_jl_NullPointerException__($thiz) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, null, null, true, true);
  return $thiz;
}
class $c_jl_NullPointerException extends $c_jl_RuntimeException {
}
var $d_jl_NullPointerException = new $TypeData().i($c_jl_NullPointerException, "java.lang.NullPointerException", ({
  dB: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
function $isArrayOf_jl_SecurityException(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.dD)));
}
function $f_jl_Short__equals__O__Z($thiz, that) {
  return Object.is($thiz, that);
}
function $f_jl_Short__hashCode__I($thiz) {
  return $thiz;
}
function $f_jl_Short__toString__T($thiz) {
  return ("" + $thiz);
}
function $f_jl_Short__compareTo__O__I($thiz, o) {
  return (($thiz - o) | 0);
}
function $isArrayOf_jl_Short(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bx)));
}
var $d_jl_Short = new $TypeData().i(0, "java.lang.Short", ({
  bx: 1,
  ai: 1,
  a: 1,
  F: 1,
  a5: 1
}), ((x) => $isShort(x)));
class $c_jl_StackOverflowError extends $c_jl_VirtualMachineError {
  constructor(s) {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, s, null, true, true);
  }
}
var $d_jl_StackOverflowError = new $TypeData().i($c_jl_StackOverflowError, "java.lang.StackOverflowError", ({
  dE: 1,
  bA: 1,
  aR: 1,
  r: 1,
  a: 1
}));
class $c_jl_UnsupportedOperationException extends $c_jl_RuntimeException {
  constructor(s) {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, s, null, true, true);
  }
}
var $d_jl_UnsupportedOperationException = new $TypeData().i($c_jl_UnsupportedOperationException, "java.lang.UnsupportedOperationException", ({
  dO: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_ConcurrentModificationException extends $c_jl_RuntimeException {
  constructor(s) {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, s, null, true, true);
  }
}
var $d_ju_ConcurrentModificationException = new $TypeData().i($c_ju_ConcurrentModificationException, "java.util.ConcurrentModificationException", ({
  dV: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
function $ct_ju_NoSuchElementException__T__($thiz, s) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, s, null, true, true);
  return $thiz;
}
function $ct_ju_NoSuchElementException__($thiz) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, null, null, true, true);
  return $thiz;
}
class $c_ju_NoSuchElementException extends $c_jl_RuntimeException {
}
var $d_ju_NoSuchElementException = new $TypeData().i($c_ju_NoSuchElementException, "java.util.NoSuchElementException", ({
  aH: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
/** @constructor */
function $c_ju_concurrent_TimeUnit$$anon$1() {
  this.dx = null;
  this.d8 = 0;
  $ct_jl_Enum__T__I__(this, "NANOSECONDS", 0);
}
$p = $c_ju_concurrent_TimeUnit$$anon$1.prototype = new $h_ju_concurrent_TimeUnit();
$p.constructor = $c_ju_concurrent_TimeUnit$$anon$1;
/** @constructor */
function $h_ju_concurrent_TimeUnit$$anon$1() {
}
$h_ju_concurrent_TimeUnit$$anon$1.prototype = $p;
$p.du = (function(a_$_lo, a_$_hi) {
  return $bL(a_$_lo, a_$_hi);
});
$p.fK = (function(a_$_lo, a_$_hi) {
  var sign = (a_$_hi >> 31);
  var xlo = (a_$_lo ^ sign);
  var rlo = ((xlo - sign) | 0);
  var rhi = (((a_$_hi ^ sign) + (((rlo >>> 0) < (xlo >>> 0)) | 0)) | 0);
  var aHat = ((4.294967296E9 * (rhi >>> 0.0)) + (rlo >>> 0.0));
  var x = (1.0000000000000016E-6 * aHat);
  var lo = (x | 0.0);
  var hi = ((2.3283064365386963E-10 * x) | 0.0);
  var rHat = ((rlo - Math.imul(1000000, lo)) | 0);
  if ((rHat < 0)) {
    var lo$1 = ((lo - 1) | 0);
    var hi$1 = ((((hi - 1) | 0) + ((lo$1 !== (-1)) | 0)) | 0);
    var absR_$_lo = lo$1;
    var absR_$_hi = hi$1;
  } else {
    var absR_$_lo = lo;
    var absR_$_hi = hi;
  }
  if ((a_$_hi < 0)) {
    var lo$2 = ((-absR_$_lo) | 0);
    var hi$2 = ((((-absR_$_hi) | 0) - ((lo$2 !== 0) | 0)) | 0);
    return $bL(lo$2, hi$2);
  } else {
    return $bL(absR_$_lo, absR_$_hi);
  }
});
var $d_ju_concurrent_TimeUnit$$anon$1 = new $TypeData().i($c_ju_concurrent_TimeUnit$$anon$1, "java.util.concurrent.TimeUnit$$anon$1", ({
  ef: 1,
  aj: 1,
  ah: 1,
  F: 1,
  a: 1
}));
/** @constructor */
function $c_ju_concurrent_TimeUnit$$anon$2() {
  this.dx = null;
  this.d8 = 0;
  $ct_jl_Enum__T__I__(this, "MICROSECONDS", 1);
}
$p = $c_ju_concurrent_TimeUnit$$anon$2.prototype = new $h_ju_concurrent_TimeUnit();
$p.constructor = $c_ju_concurrent_TimeUnit$$anon$2;
/** @constructor */
function $h_ju_concurrent_TimeUnit$$anon$2() {
}
$h_ju_concurrent_TimeUnit$$anon$2.prototype = $p;
$p.du = (function(a_$_lo, a_$_hi) {
  return $m_ju_concurrent_TimeUnit$().dW(a_$_lo, a_$_hi, 1000, 0, (-1511828489), 2147483);
});
$p.fK = (function(a_$_lo, a_$_hi) {
  var sign = (a_$_hi >> 31);
  var xlo = (a_$_lo ^ sign);
  var rlo = ((xlo - sign) | 0);
  var rhi = (((a_$_hi ^ sign) + (((rlo >>> 0) < (xlo >>> 0)) | 0)) | 0);
  var quotHi = (((rhi >>> 0) / 1000) | 0);
  var k = ((rhi - Math.imul(1000, quotHi)) | 0);
  var quotLo = ((0.0010000000000000005 * ((4.294967296E9 * k) + (rlo >>> 0.0))) | 0.0);
  if ((a_$_hi < 0)) {
    var lo = ((-quotLo) | 0);
    var hi = ((((-quotHi) | 0) - ((lo !== 0) | 0)) | 0);
    return $bL(lo, hi);
  } else {
    return $bL(quotLo, quotHi);
  }
});
var $d_ju_concurrent_TimeUnit$$anon$2 = new $TypeData().i($c_ju_concurrent_TimeUnit$$anon$2, "java.util.concurrent.TimeUnit$$anon$2", ({
  eg: 1,
  aj: 1,
  ah: 1,
  F: 1,
  a: 1
}));
/** @constructor */
function $c_ju_concurrent_TimeUnit$$anon$3() {
  this.dx = null;
  this.d8 = 0;
  $ct_jl_Enum__T__I__(this, "MILLISECONDS", 2);
}
$p = $c_ju_concurrent_TimeUnit$$anon$3.prototype = new $h_ju_concurrent_TimeUnit();
$p.constructor = $c_ju_concurrent_TimeUnit$$anon$3;
/** @constructor */
function $h_ju_concurrent_TimeUnit$$anon$3() {
}
$h_ju_concurrent_TimeUnit$$anon$3.prototype = $p;
$p.du = (function(a_$_lo, a_$_hi) {
  return $m_ju_concurrent_TimeUnit$().dW(a_$_lo, a_$_hi, 1000000, 0, 2077252342, 2147);
});
$p.fK = (function(a_$_lo, a_$_hi) {
  return $bL(a_$_lo, a_$_hi);
});
var $d_ju_concurrent_TimeUnit$$anon$3 = new $TypeData().i($c_ju_concurrent_TimeUnit$$anon$3, "java.util.concurrent.TimeUnit$$anon$3", ({
  eh: 1,
  aj: 1,
  ah: 1,
  F: 1,
  a: 1
}));
/** @constructor */
function $c_ju_concurrent_TimeUnit$$anon$4() {
  this.dx = null;
  this.d8 = 0;
  $ct_jl_Enum__T__I__(this, "SECONDS", 3);
}
$p = $c_ju_concurrent_TimeUnit$$anon$4.prototype = new $h_ju_concurrent_TimeUnit();
$p.constructor = $c_ju_concurrent_TimeUnit$$anon$4;
/** @constructor */
function $h_ju_concurrent_TimeUnit$$anon$4() {
}
$h_ju_concurrent_TimeUnit$$anon$4.prototype = $p;
$p.du = (function(a_$_lo, a_$_hi) {
  return $m_ju_concurrent_TimeUnit$().dW(a_$_lo, a_$_hi, 1000000000, 0, 633437444, 2);
});
$p.fK = (function(a_$_lo, a_$_hi) {
  return $m_ju_concurrent_TimeUnit$().dW(a_$_lo, a_$_hi, 1000, 0, (-1511828489), 2147483);
});
var $d_ju_concurrent_TimeUnit$$anon$4 = new $TypeData().i($c_ju_concurrent_TimeUnit$$anon$4, "java.util.concurrent.TimeUnit$$anon$4", ({
  ei: 1,
  aj: 1,
  ah: 1,
  F: 1,
  a: 1
}));
/** @constructor */
function $c_ju_concurrent_TimeUnit$$anon$5() {
  this.dx = null;
  this.d8 = 0;
  $ct_jl_Enum__T__I__(this, "MINUTES", 4);
}
$p = $c_ju_concurrent_TimeUnit$$anon$5.prototype = new $h_ju_concurrent_TimeUnit();
$p.constructor = $c_ju_concurrent_TimeUnit$$anon$5;
/** @constructor */
function $h_ju_concurrent_TimeUnit$$anon$5() {
}
$h_ju_concurrent_TimeUnit$$anon$5.prototype = $p;
$p.du = (function(a_$_lo, a_$_hi) {
  return $m_ju_concurrent_TimeUnit$().dW(a_$_lo, a_$_hi, (-129542144), 13, 153722867, 0);
});
$p.fK = (function(a_$_lo, a_$_hi) {
  return $m_ju_concurrent_TimeUnit$().dW(a_$_lo, a_$_hi, 60000, 0, 1692789776, 35791);
});
var $d_ju_concurrent_TimeUnit$$anon$5 = new $TypeData().i($c_ju_concurrent_TimeUnit$$anon$5, "java.util.concurrent.TimeUnit$$anon$5", ({
  ej: 1,
  aj: 1,
  ah: 1,
  F: 1,
  a: 1
}));
/** @constructor */
function $c_ju_concurrent_TimeUnit$$anon$6() {
  this.dx = null;
  this.d8 = 0;
  $ct_jl_Enum__T__I__(this, "HOURS", 5);
}
$p = $c_ju_concurrent_TimeUnit$$anon$6.prototype = new $h_ju_concurrent_TimeUnit();
$p.constructor = $c_ju_concurrent_TimeUnit$$anon$6;
/** @constructor */
function $h_ju_concurrent_TimeUnit$$anon$6() {
}
$h_ju_concurrent_TimeUnit$$anon$6.prototype = $p;
$p.du = (function(a_$_lo, a_$_hi) {
  return $m_ju_concurrent_TimeUnit$().dW(a_$_lo, a_$_hi, 817405952, 838, 2562047, 0);
});
$p.fK = (function(a_$_lo, a_$_hi) {
  return $m_ju_concurrent_TimeUnit$().dW(a_$_lo, a_$_hi, 3600000, 0, (-2047687697), 596);
});
var $d_ju_concurrent_TimeUnit$$anon$6 = new $TypeData().i($c_ju_concurrent_TimeUnit$$anon$6, "java.util.concurrent.TimeUnit$$anon$6", ({
  ek: 1,
  aj: 1,
  ah: 1,
  F: 1,
  a: 1
}));
/** @constructor */
function $c_ju_concurrent_TimeUnit$$anon$7() {
  this.dx = null;
  this.d8 = 0;
  $ct_jl_Enum__T__I__(this, "DAYS", 6);
}
$p = $c_ju_concurrent_TimeUnit$$anon$7.prototype = new $h_ju_concurrent_TimeUnit();
$p.constructor = $c_ju_concurrent_TimeUnit$$anon$7;
/** @constructor */
function $h_ju_concurrent_TimeUnit$$anon$7() {
}
$h_ju_concurrent_TimeUnit$$anon$7.prototype = $p;
$p.du = (function(a_$_lo, a_$_hi) {
  return $m_ju_concurrent_TimeUnit$().dW(a_$_lo, a_$_hi, (-1857093632), 20116, 106751, 0);
});
$p.fK = (function(a_$_lo, a_$_hi) {
  return $m_ju_concurrent_TimeUnit$().dW(a_$_lo, a_$_hi, 86400000, 0, (-622191233), 24);
});
var $d_ju_concurrent_TimeUnit$$anon$7 = new $TypeData().i($c_ju_concurrent_TimeUnit$$anon$7, "java.util.concurrent.TimeUnit$$anon$7", ({
  el: 1,
  aj: 1,
  ah: 1,
  F: 1,
  a: 1
}));
function $p_s_MatchError__objString__T($thiz) {
  if ((!$thiz.ms)) {
    if (($thiz.ia === null)) {
      var $x_1 = "null";
    } else {
      var this$1 = $thiz.ia;
      var cls = $objectGetClass(this$1);
      var ofClass = ((cls === null) ? "of a JS class" : ("of class " + cls.J.N));
      try {
        var $x_1 = ((($thiz.ia + " (") + ofClass) + ")");
      } catch (e) {
        var $x_1 = ("an instance " + ofClass);
      }
    }
    $thiz.mr = $x_1;
    $thiz.ms = true;
  }
  return $thiz.mr;
}
class $c_s_MatchError extends $c_jl_RuntimeException {
  constructor(obj) {
    super();
    this.ia = null;
    this.mr = null;
    this.ms = false;
    this.ia = obj;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return $p_s_MatchError__objString__T(this);
  }
}
var $d_s_MatchError = new $TypeData().i($c_s_MatchError, "scala.MatchError", ({
  eK: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
/** @constructor */
function $c_s_Option() {
}
$p = $c_s_Option.prototype = new $h_O();
$p.constructor = $c_s_Option;
/** @constructor */
function $h_s_Option() {
}
$h_s_Option.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.g = (function() {
  return (this === $m_s_None$());
});
$p.q = (function() {
  return ((!this.g()) | 0);
});
$p.c = (function() {
  return (this.g() ? $m_sc_Iterator$().G : new $c_sc_Iterator$$anon$20(this.S()));
});
/** @constructor */
function $c_s_PartialFunction$$anon$2(f$2) {
  this.mu = null;
  this.mu = f$2;
}
$p = $c_s_PartialFunction$$anon$2.prototype = new $h_sr_AbstractPartialFunction();
$p.constructor = $c_s_PartialFunction$$anon$2;
/** @constructor */
function $h_s_PartialFunction$$anon$2() {
}
$h_s_PartialFunction$$anon$2.prototype = $p;
$p.cF = (function(x) {
  return true;
});
$p.bQ = (function(x, default$1) {
  return this.mu.d(x);
});
var $d_s_PartialFunction$$anon$2 = new $TypeData().i($c_s_PartialFunction$$anon$2, "scala.PartialFunction$$anon$2", ({
  eP: 1,
  bc: 1,
  i: 1,
  j: 1,
  a: 1
}));
/** @constructor */
function $c_s_PartialFunction$OrElse(f1, f2) {
  this.ib = null;
  this.ic = null;
  this.ib = f1;
  this.ic = f2;
}
$p = $c_s_PartialFunction$OrElse.prototype = new $h_sr_AbstractPartialFunction();
$p.constructor = $c_s_PartialFunction$OrElse;
/** @constructor */
function $h_s_PartialFunction$OrElse() {
}
$h_s_PartialFunction$OrElse.prototype = $p;
$p.cF = (function(x) {
  return (this.ib.cF(x) || this.ic.cF(x));
});
$p.d = (function(x) {
  return this.ib.bQ(x, this.ic);
});
$p.bQ = (function(x, default$1) {
  var z = this.ib.bQ(x, $m_s_PartialFunction$().gP);
  return ((!$m_s_PartialFunction$().oL(z)) ? z : this.ic.bQ(x, default$1));
});
var $d_s_PartialFunction$OrElse = new $TypeData().i($c_s_PartialFunction$OrElse, "scala.PartialFunction$OrElse", ({
  eR: 1,
  bc: 1,
  i: 1,
  j: 1,
  a: 1
}));
/** @constructor */
function $c_s_Product$$anon$1(outer) {
  this.gQ = 0;
  this.mx = 0;
  this.mw = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.mw = outer;
  this.gQ = 0;
  this.mx = outer.bE();
}
$p = $c_s_Product$$anon$1.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_s_Product$$anon$1;
/** @constructor */
function $h_s_Product$$anon$1() {
}
$h_s_Product$$anon$1.prototype = $p;
$p.k = (function() {
  return (this.gQ < this.mx);
});
$p.f = (function() {
  var result = this.mw.bF(this.gQ);
  this.gQ = ((1 + this.gQ) | 0);
  return result;
});
var $d_s_Product$$anon$1 = new $TypeData().i($c_s_Product$$anon$1, "scala.Product$$anon$1", ({
  eT: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
function $ct_T2__O__O__($thiz, _1, _2) {
  $thiz.jB = _1;
  $thiz.jC = _2;
  return $thiz;
}
/** @constructor */
function $c_T2() {
  this.jB = null;
  this.jC = null;
}
$p = $c_T2.prototype = new $h_O();
$p.constructor = $c_T2;
/** @constructor */
function $h_T2() {
}
$h_T2.prototype = $p;
$p.bE = (function() {
  return 2;
});
$p.bF = (function(n) {
  return $f_s_Product2__productElement__I__O(this, n);
});
$p.aw = (function() {
  return this.jB;
});
$p.ap = (function() {
  return this.jC;
});
$p.t = (function() {
  return (((("(" + this.aw()) + ",") + this.ap()) + ")");
});
$p.bG = (function() {
  return "Tuple2";
});
$p.cd = (function() {
  return new $c_sr_ScalaRunTime$$anon$1(this);
});
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().dr(this, (-116390334), true);
});
$p.p = (function(x$1) {
  return ((this === x$1) || ((x$1 instanceof $c_T2) && ($m_sr_BoxesRunTime$().n(this.aw(), x$1.aw()) && $m_sr_BoxesRunTime$().n(this.ap(), x$1.ap()))));
});
$p.nE = (function() {
  return (this.aw() | 0);
});
$p.kk = (function() {
  return (this.ap() | 0);
});
function $isArrayOf_T2(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.aW)));
}
var $d_T2 = new $TypeData().i($c_T2, "scala.Tuple2", ({
  aW: 1,
  bE: 1,
  J: 1,
  d: 1,
  a: 1
}));
/** @constructor */
function $c_T3(_1, _2, _3) {
  this.g5 = null;
  this.g6 = null;
  this.g7 = null;
  this.g5 = _1;
  this.g6 = _2;
  this.g7 = _3;
}
$p = $c_T3.prototype = new $h_O();
$p.constructor = $c_T3;
/** @constructor */
function $h_T3() {
}
$h_T3.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.bE = (function() {
  return 3;
});
$p.bF = (function(n) {
  return $f_s_Product3__productElement__I__O(this, n);
});
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().dr(this, (-192629203), true);
});
$p.p = (function(x$0) {
  return ((this === x$0) || ((x$0 instanceof $c_T3) && (($m_sr_BoxesRunTime$().n(this.g5, x$0.g5) && $m_sr_BoxesRunTime$().n(this.g6, x$0.g6)) && $m_sr_BoxesRunTime$().n(this.g7, x$0.g7))));
});
$p.bG = (function() {
  return "Tuple3";
});
$p.t = (function() {
  return (((((("(" + this.g5) + ",") + this.g6) + ",") + this.g7) + ")");
});
function $isArrayOf_T3(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bG)));
}
var $d_T3 = new $TypeData().i($c_T3, "scala.Tuple3", ({
  bG: 1,
  d: 1,
  J: 1,
  eV: 1,
  a: 1
}));
/** @constructor */
function $c_sc_ClassTagSeqFactory$AnySeqDelegate(delegate) {
  this.gS = null;
  $ct_sc_ClassTagIterableFactory$AnyIterableDelegate__sc_ClassTagIterableFactory__(this, delegate);
}
$p = $c_sc_ClassTagSeqFactory$AnySeqDelegate.prototype = new $h_sc_ClassTagIterableFactory$AnyIterableDelegate();
$p.constructor = $c_sc_ClassTagSeqFactory$AnySeqDelegate;
/** @constructor */
function $h_sc_ClassTagSeqFactory$AnySeqDelegate() {
}
$h_sc_ClassTagSeqFactory$AnySeqDelegate.prototype = $p;
var $d_sc_ClassTagSeqFactory$AnySeqDelegate = new $TypeData().i($c_sc_ClassTagSeqFactory$AnySeqDelegate, "scala.collection.ClassTagSeqFactory$AnySeqDelegate", ({
  f2: 1,
  f1: 1,
  a: 1,
  B: 1,
  W: 1
}));
function $f_sc_IndexedSeqOps__prepended__O__O($thiz, elem) {
  return $thiz.aH().a3(new $c_sc_IndexedSeqView$Prepended(elem, $thiz));
}
function $f_sc_IndexedSeqOps__map__F1__O($thiz, f) {
  return $thiz.aH().a3($ct_sc_IndexedSeqView$Map__sc_IndexedSeqOps__F1__(new $c_sc_IndexedSeqView$Map(), $thiz, f));
}
function $f_sc_IndexedSeqOps__head__O($thiz) {
  if ((!$thiz.g())) {
    return $thiz.r(0);
  } else {
    throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), ("head of empty " + ($is_sc_IndexedSeq($thiz) ? $thiz.bK() : $thiz.t())));
  }
}
function $f_sc_Iterable__toString__T($thiz) {
  return $f_sc_IterableOnceOps__mkString__T__T__T__T($thiz, ($thiz.bK() + "("), ", ", ")");
}
function $is_sc_Iterable(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.e)));
}
function $isArrayOf_sc_Iterable(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.e)));
}
/** @constructor */
function $c_sc_Iterator$$anon$16(outer) {
  this.ij = 0;
  this.ii = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.ii = outer;
  this.ij = 0;
}
$p = $c_sc_Iterator$$anon$16.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_Iterator$$anon$16;
/** @constructor */
function $h_sc_Iterator$$anon$16() {
}
$h_sc_Iterator$$anon$16.prototype = $p;
$p.q = (function() {
  return this.ii.q();
});
$p.k = (function() {
  return this.ii.k();
});
$p.gw = (function() {
  var ret = $ct_T2__O__O__(new $c_T2(), this.ii.f(), this.ij);
  this.ij = ((1 + this.ij) | 0);
  return ret;
});
$p.f = (function() {
  return this.gw();
});
var $d_sc_Iterator$$anon$16 = new $TypeData().i($c_sc_Iterator$$anon$16, "scala.collection.Iterator$$anon$16", ({
  fa: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sc_Iterator$$anon$19() {
}
$p = $c_sc_Iterator$$anon$19.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_Iterator$$anon$19;
/** @constructor */
function $h_sc_Iterator$$anon$19() {
}
$h_sc_Iterator$$anon$19.prototype = $p;
$p.k = (function() {
  return false;
});
$p.rA = (function() {
  throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), "next on empty iterator");
});
$p.q = (function() {
  return 0;
});
$p.f = (function() {
  this.rA();
});
$p.gy = (function(from, until) {
  return this;
});
var $d_sc_Iterator$$anon$19 = new $TypeData().i($c_sc_Iterator$$anon$19, "scala.collection.Iterator$$anon$19", ({
  fb: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sc_Iterator$$anon$20(a$2) {
  this.mA = null;
  this.gU = false;
  this.mA = a$2;
  this.gU = false;
}
$p = $c_sc_Iterator$$anon$20.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_Iterator$$anon$20;
/** @constructor */
function $h_sc_Iterator$$anon$20() {
}
$h_sc_Iterator$$anon$20.prototype = $p;
$p.k = (function() {
  return (!this.gU);
});
$p.f = (function() {
  if (this.gU) {
    return $m_sc_Iterator$().G.f();
  } else {
    this.gU = true;
    return this.mA;
  }
});
$p.gy = (function(from, until) {
  return (((this.gU || (from > 0)) || (until === 0)) ? $m_sc_Iterator$().G : this);
});
var $d_sc_Iterator$$anon$20 = new $TypeData().i($c_sc_Iterator$$anon$20, "scala.collection.Iterator$$anon$20", ({
  fc: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sc_Iterator$$anon$8(f$8, outer) {
  this.mD = null;
  this.mF = null;
  this.ik = false;
  this.mE = null;
  this.jJ = null;
  this.mD = f$8;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.jJ = outer;
  this.mF = $ct_scm_HashSet__(new $c_scm_HashSet());
  this.ik = false;
}
$p = $c_sc_Iterator$$anon$8.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_Iterator$$anon$8;
/** @constructor */
function $h_sc_Iterator$$anon$8() {
}
$h_sc_Iterator$$anon$8.prototype = $p;
$p.k = (function() {
  while (true) {
    if (this.ik) {
      return true;
    } else if (this.jJ.k()) {
      var a = this.jJ.f();
      if ((!this.mF.iN(this.mD.d(a)))) {
        continue;
      }
      this.mE = a;
      this.ik = true;
      return true;
    } else {
      return false;
    }
  }
});
$p.f = (function() {
  if (this.k()) {
    this.ik = false;
    return this.mE;
  } else {
    return $m_sc_Iterator$().G.f();
  }
});
var $d_sc_Iterator$$anon$8 = new $TypeData().i($c_sc_Iterator$$anon$8, "scala.collection.Iterator$$anon$8", ({
  ff: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sc_Iterator$$anon$9(f$9, outer) {
  this.mG = null;
  this.il = null;
  this.mG = f$9;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.il = outer;
}
$p = $c_sc_Iterator$$anon$9.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_Iterator$$anon$9;
/** @constructor */
function $h_sc_Iterator$$anon$9() {
}
$h_sc_Iterator$$anon$9.prototype = $p;
$p.q = (function() {
  return this.il.q();
});
$p.k = (function() {
  return this.il.k();
});
$p.f = (function() {
  return this.mG.d(this.il.f());
});
var $d_sc_Iterator$$anon$9 = new $TypeData().i($c_sc_Iterator$$anon$9, "scala.collection.Iterator$$anon$9", ({
  fg: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
function $p_sc_Iterator$ConcatIterator__merge$1__V($thiz) {
  while (true) {
    if (($thiz.cg instanceof $c_sc_Iterator$ConcatIterator)) {
      var c = $thiz.cg;
      $thiz.cg = c.cg;
      $thiz.ew = c.ew;
      if ((c.cO !== null)) {
        if (($thiz.cN === null)) {
          $thiz.cN = c.cN;
        }
        var x$proxy10 = c.cN;
        if ((x$proxy10 === null)) {
          $m_sr_Scala3RunTime$().bV();
        }
        x$proxy10.gV = $thiz.cO;
        $thiz.cO = c.cO;
      }
    } else {
      return (void 0);
    }
  }
}
function $p_sc_Iterator$ConcatIterator__advance$1__Z($thiz) {
  while (true) {
    if (($thiz.cO === null)) {
      $thiz.cg = null;
      $thiz.cN = null;
      return false;
    } else {
      $thiz.cg = $thiz.cO.ra();
      if (($thiz.cN === $thiz.cO)) {
        var x$proxy12 = $thiz.cN;
        if ((x$proxy12 === null)) {
          $m_sr_Scala3RunTime$().bV();
        }
        $thiz.cN = x$proxy12.gV;
      }
      $thiz.cO = $thiz.cO.gV;
      $p_sc_Iterator$ConcatIterator__merge$1__V($thiz);
      if ($thiz.ew) {
        return true;
      } else {
        if ((!(($thiz.cg !== null) && $thiz.cg.k()))) {
          continue;
        }
        $thiz.ew = true;
        return true;
      }
    }
  }
}
/** @constructor */
function $c_sc_Iterator$ConcatIterator(from) {
  this.cg = null;
  this.cO = null;
  this.cN = null;
  this.ew = false;
  this.cg = from;
  this.cO = null;
  this.cN = null;
  this.ew = false;
}
$p = $c_sc_Iterator$ConcatIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_Iterator$ConcatIterator;
/** @constructor */
function $h_sc_Iterator$ConcatIterator() {
}
$h_sc_Iterator$ConcatIterator.prototype = $p;
$p.k = (function() {
  if (this.ew) {
    return true;
  } else if ((this.cg !== null)) {
    if (this.cg.k()) {
      this.ew = true;
      return true;
    } else {
      return $p_sc_Iterator$ConcatIterator__advance$1__Z(this);
    }
  } else {
    return false;
  }
});
$p.f = (function() {
  if (this.k()) {
    this.ew = false;
    var x$proxy13 = this.cg;
    if ((x$proxy13 === null)) {
      $m_sr_Scala3RunTime$().bV();
    }
    return x$proxy13.f();
  } else {
    return $m_sc_Iterator$().G.f();
  }
});
$p.fB = (function(that) {
  var c = new $c_sc_Iterator$ConcatIteratorCell(that, null);
  if ((this.cO === null)) {
    this.cO = c;
    this.cN = c;
  } else {
    var x$proxy14 = this.cN;
    if ((x$proxy14 === null)) {
      $m_sr_Scala3RunTime$().bV();
    }
    x$proxy14.gV = c;
    this.cN = c;
  }
  if ((this.cg === null)) {
    this.cg = $m_sc_Iterator$().G;
  }
  return this;
});
function $isArrayOf_sc_Iterator$ConcatIterator(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bQ)));
}
var $d_sc_Iterator$ConcatIterator = new $TypeData().i($c_sc_Iterator$ConcatIterator, "scala.collection.Iterator$ConcatIterator", ({
  bQ: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
function $p_sc_Iterator$SliceIterator__skip__V($thiz) {
  while (($thiz.e4 > 0)) {
    if ($thiz.ex.k()) {
      $thiz.ex.f();
      $thiz.e4 = (($thiz.e4 - 1) | 0);
    } else {
      $thiz.e4 = 0;
    }
  }
}
function $p_sc_Iterator$SliceIterator__adjustedBound$1__I__I($thiz, lo$1) {
  if (($thiz.cv < 0)) {
    return (-1);
  } else {
    var that = (($thiz.cv - lo$1) | 0);
    return ((that < 0) ? 0 : that);
  }
}
/** @constructor */
function $c_sc_Iterator$SliceIterator(underlying, start, limit) {
  this.ex = null;
  this.cv = 0;
  this.e4 = 0;
  this.ex = underlying;
  this.cv = limit;
  this.e4 = start;
}
$p = $c_sc_Iterator$SliceIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_Iterator$SliceIterator;
/** @constructor */
function $h_sc_Iterator$SliceIterator() {
}
$h_sc_Iterator$SliceIterator.prototype = $p;
$p.q = (function() {
  var size = this.ex.q();
  if ((size < 0)) {
    return (-1);
  } else {
    var that = ((size - this.e4) | 0);
    var dropSize = ((that < 0) ? 0 : that);
    if ((this.cv < 0)) {
      return dropSize;
    } else {
      var x = this.cv;
      return ((x < dropSize) ? x : dropSize);
    }
  }
});
$p.k = (function() {
  $p_sc_Iterator$SliceIterator__skip__V(this);
  return ((this.cv !== 0) && this.ex.k());
});
$p.f = (function() {
  $p_sc_Iterator$SliceIterator__skip__V(this);
  if ((this.cv > 0)) {
    this.cv = ((this.cv - 1) | 0);
    return this.ex.f();
  } else {
    return ((this.cv < 0) ? this.ex.f() : $m_sc_Iterator$().G.f());
  }
});
$p.gy = (function(from, until) {
  var lo = ((from > 0) ? from : 0);
  if ((until < 0)) {
    var rest = $p_sc_Iterator$SliceIterator__adjustedBound$1__I__I(this, lo);
  } else if ((until <= lo)) {
    var rest = 0;
  } else if ((this.cv < 0)) {
    var rest = ((until - lo) | 0);
  } else {
    var x = $p_sc_Iterator$SliceIterator__adjustedBound$1__I__I(this, lo);
    var that = ((until - lo) | 0);
    var rest = ((x < that) ? x : that);
  }
  var sum = ((this.e4 + lo) | 0);
  if ((rest === 0)) {
    return $m_sc_Iterator$().G;
  } else if ((sum < 0)) {
    this.e4 = 2147483647;
    this.cv = 0;
    return $f_sc_Iterator__concat__F0__sc_Iterator(this, new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => new $c_sc_Iterator$SliceIterator(this.ex, ((sum - 2147483647) | 0), rest))));
  } else {
    this.e4 = sum;
    this.cv = rest;
    return this;
  }
});
var $d_sc_Iterator$SliceIterator = new $TypeData().i($c_sc_Iterator$SliceIterator, "scala.collection.Iterator$SliceIterator", ({
  fi: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
function $f_sc_LinearSeqOps__length__I($thiz) {
  var these = $thiz;
  var len = 0;
  while ((!these.g())) {
    len = ((1 + len) | 0);
    these = these.o();
  }
  return len;
}
function $f_sc_LinearSeqOps__lengthCompare__I__I($thiz, len) {
  return ((len < 0) ? 1 : $p_sc_LinearSeqOps__loop$1__I__I__sc_LinearSeq__I($thiz, len, 0, $thiz));
}
function $f_sc_LinearSeqOps__isDefinedAt__I__Z($thiz, x) {
  return ((x >= 0) && ($thiz.aI(x) > 0));
}
function $f_sc_LinearSeqOps__apply__I__O($thiz, n) {
  if ((n < 0)) {
    throw $ct_jl_IndexOutOfBoundsException__T__(new $c_jl_IndexOutOfBoundsException(), ("" + n));
  }
  var skipped = $thiz.ky(n);
  if (skipped.g()) {
    throw $ct_jl_IndexOutOfBoundsException__T__(new $c_jl_IndexOutOfBoundsException(), ("" + n));
  }
  return skipped.m();
}
function $f_sc_LinearSeqOps__exists__F1__Z($thiz, p) {
  var these = $thiz;
  while ((!these.g())) {
    if ((!(!p.d(these.m())))) {
      return true;
    }
    these = these.o();
  }
  return false;
}
function $f_sc_LinearSeqOps__foldLeft__O__F2__O($thiz, z, op) {
  var acc = z;
  var these = $thiz;
  while ((!these.g())) {
    acc = op.ca(acc, these.m());
    these = these.o();
  }
  return acc;
}
function $f_sc_LinearSeqOps__sameElements__sc_IterableOnce__Z($thiz, that) {
  return ($is_sc_LinearSeq(that) ? $p_sc_LinearSeqOps__linearSeqEq$1__sc_LinearSeq__sc_LinearSeq__Z($thiz, $thiz, that) : $f_sc_SeqOps__sameElements__sc_IterableOnce__Z($thiz, that));
}
function $f_sc_LinearSeqOps__indexWhere__F1__I__I($thiz, p, from) {
  var i = ((from > 0) ? from : 0);
  var these = $thiz.ky(from);
  while ((!these.g())) {
    if ((!(!p.d(these.m())))) {
      return i;
    }
    i = ((1 + i) | 0);
    these = these.o();
  }
  return (-1);
}
function $p_sc_LinearSeqOps__loop$1__I__I__sc_LinearSeq__I($thiz, len$1, i, xs) {
  var xs$tailLocal1 = xs;
  var i$tailLocal1 = i;
  while (true) {
    if ((i$tailLocal1 === len$1)) {
      return ((!xs$tailLocal1.g()) | 0);
    } else {
      if ((!xs$tailLocal1.g())) {
        var i$tailLocal1$tmp1 = ((1 + i$tailLocal1) | 0);
        var xs$tailLocal1$tmp1 = xs$tailLocal1.o();
        i$tailLocal1 = i$tailLocal1$tmp1;
        xs$tailLocal1 = xs$tailLocal1$tmp1;
        continue;
      }
      return (-1);
    }
  }
}
function $p_sc_LinearSeqOps__linearSeqEq$1__sc_LinearSeq__sc_LinearSeq__Z($thiz, a, b) {
  var b$tailLocal1 = b;
  var a$tailLocal1 = a;
  while (true) {
    if ((a$tailLocal1 === b$tailLocal1)) {
      return true;
    } else {
      if ((((!a$tailLocal1.g()) && (!b$tailLocal1.g())) && $m_sr_BoxesRunTime$().n(a$tailLocal1.m(), b$tailLocal1.m()))) {
        var a$tailLocal1$tmp1 = a$tailLocal1.o();
        var b$tailLocal1$tmp1 = b$tailLocal1.o();
        a$tailLocal1 = a$tailLocal1$tmp1;
        b$tailLocal1 = b$tailLocal1$tmp1;
        continue;
      }
      return (a$tailLocal1.g() && b$tailLocal1.g());
    }
  }
}
/** @constructor */
function $c_sc_Seq$() {
  this.e5 = null;
  $ct_sc_SeqFactory$Delegate__sc_SeqFactory__(this, $m_sci_Seq$());
}
$p = $c_sc_Seq$.prototype = new $h_sc_SeqFactory$Delegate();
$p.constructor = $c_sc_Seq$;
/** @constructor */
function $h_sc_Seq$() {
}
$h_sc_Seq$.prototype = $p;
var $d_sc_Seq$ = new $TypeData().i($c_sc_Seq$, "scala.collection.Seq$", ({
  fm: 1,
  aD: 1,
  a: 1,
  B: 1,
  W: 1
}));
var $n_sc_Seq$;
function $m_sc_Seq$() {
  if ((!$n_sc_Seq$)) {
    $n_sc_Seq$ = new $c_sc_Seq$();
  }
  return $n_sc_Seq$;
}
/** @constructor */
function $c_sc_StrictOptimizedLinearSeqOps$$anon$1(outer) {
  this.h0 = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.h0 = outer;
}
$p = $c_sc_StrictOptimizedLinearSeqOps$$anon$1.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_StrictOptimizedLinearSeqOps$$anon$1;
/** @constructor */
function $h_sc_StrictOptimizedLinearSeqOps$$anon$1() {
}
$h_sc_StrictOptimizedLinearSeqOps$$anon$1.prototype = $p;
$p.k = (function() {
  return (!this.h0.g());
});
$p.f = (function() {
  var r = this.h0.m();
  this.h0 = this.h0.o();
  return r;
});
var $d_sc_StrictOptimizedLinearSeqOps$$anon$1 = new $TypeData().i($c_sc_StrictOptimizedLinearSeqOps$$anon$1, "scala.collection.StrictOptimizedLinearSeqOps$$anon$1", ({
  fr: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
function $ct_sci_ChampBaseIterator__($thiz) {
  $thiz.aK = 0;
  $thiz.ey = 0;
  $thiz.bM = (-1);
  return $thiz;
}
function $p_sci_ChampBaseIterator__initNodes__V($thiz) {
  if (($thiz.cR === null)) {
    $thiz.cR = new $ac_I(($m_sci_Node$().h7 << 1));
    $thiz.ez = new ($d_sci_Node.r().C)($m_sci_Node$().h7);
  }
}
function $ct_sci_ChampBaseIterator__sci_Node__($thiz, rootNode) {
  $ct_sci_ChampBaseIterator__($thiz);
  if (rootNode.hK()) {
    $p_sci_ChampBaseIterator__pushNode__sci_Node__V($thiz, rootNode);
  }
  if (rootNode.gs()) {
    $p_sci_ChampBaseIterator__setupPayloadNode__sci_Node__V($thiz, rootNode);
  }
  return $thiz;
}
function $p_sci_ChampBaseIterator__setupPayloadNode__sci_Node__V($thiz, node) {
  $thiz.cj = node;
  $thiz.aK = 0;
  $thiz.ey = node.hQ();
}
function $p_sci_ChampBaseIterator__pushNode__sci_Node__V($thiz, node) {
  $p_sci_ChampBaseIterator__initNodes__V($thiz);
  $thiz.bM = ((1 + $thiz.bM) | 0);
  var cursorIndex = ($thiz.bM << 1);
  var lengthIndex = ((1 + ($thiz.bM << 1)) | 0);
  $thiz.ez.a[$thiz.bM] = node;
  $thiz.cR.a[cursorIndex] = 0;
  $thiz.cR.a[lengthIndex] = node.hO();
}
function $p_sci_ChampBaseIterator__popNode__V($thiz) {
  $thiz.bM = (($thiz.bM - 1) | 0);
}
function $p_sci_ChampBaseIterator__searchNextValueNode__Z($thiz) {
  while (($thiz.bM >= 0)) {
    var cursorIndex = ($thiz.bM << 1);
    var lengthIndex = ((1 + ($thiz.bM << 1)) | 0);
    var nodeCursor = $thiz.cR.a[cursorIndex];
    if ((nodeCursor < $thiz.cR.a[lengthIndex])) {
      var \u03b41$ = $thiz.cR;
      \u03b41$.a[cursorIndex] = ((1 + \u03b41$.a[cursorIndex]) | 0);
      var nextNode = $thiz.ez.a[$thiz.bM].hJ(nodeCursor);
      if (nextNode.hK()) {
        $p_sci_ChampBaseIterator__pushNode__sci_Node__V($thiz, nextNode);
      }
      if (nextNode.gs()) {
        $p_sci_ChampBaseIterator__setupPayloadNode__sci_Node__V($thiz, nextNode);
        return true;
      }
    } else {
      $p_sci_ChampBaseIterator__popNode__V($thiz);
    }
  }
  return false;
}
/** @constructor */
function $c_sci_ChampBaseIterator() {
  this.aK = 0;
  this.ey = 0;
  this.cj = null;
  this.bM = 0;
  this.cR = null;
  this.ez = null;
}
$p = $c_sci_ChampBaseIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sci_ChampBaseIterator;
/** @constructor */
function $h_sci_ChampBaseIterator() {
}
$h_sci_ChampBaseIterator.prototype = $p;
$p.k = (function() {
  return ((this.aK < this.ey) || $p_sci_ChampBaseIterator__searchNextValueNode__Z(this));
});
function $ct_sci_ChampBaseReverseIterator__($thiz) {
  $thiz.eA = (-1);
  $thiz.cx = (-1);
  $thiz.h2 = new $ac_I(((1 + $m_sci_Node$().h7) | 0));
  $thiz.h3 = new ($d_sci_Node.r().C)(((1 + $m_sci_Node$().h7) | 0));
  return $thiz;
}
function $ct_sci_ChampBaseReverseIterator__sci_Node__($thiz, rootNode) {
  $ct_sci_ChampBaseReverseIterator__($thiz);
  $p_sci_ChampBaseReverseIterator__pushNode__sci_Node__V($thiz, rootNode);
  $p_sci_ChampBaseReverseIterator__searchNextValueNode__Z($thiz);
  return $thiz;
}
function $p_sci_ChampBaseReverseIterator__setupPayloadNode__sci_Node__V($thiz, node) {
  $thiz.ir = node;
  $thiz.eA = ((node.hQ() - 1) | 0);
}
function $p_sci_ChampBaseReverseIterator__pushNode__sci_Node__V($thiz, node) {
  $thiz.cx = ((1 + $thiz.cx) | 0);
  $thiz.h3.a[$thiz.cx] = node;
  $thiz.h2.a[$thiz.cx] = ((node.hO() - 1) | 0);
}
function $p_sci_ChampBaseReverseIterator__popNode__V($thiz) {
  $thiz.cx = (($thiz.cx - 1) | 0);
}
function $p_sci_ChampBaseReverseIterator__searchNextValueNode__Z($thiz) {
  while (($thiz.cx >= 0)) {
    var nodeCursor = $thiz.h2.a[$thiz.cx];
    $thiz.h2.a[$thiz.cx] = ((nodeCursor - 1) | 0);
    if ((nodeCursor >= 0)) {
      $p_sci_ChampBaseReverseIterator__pushNode__sci_Node__V($thiz, $thiz.h3.a[$thiz.cx].hJ(nodeCursor));
    } else {
      var currNode = $thiz.h3.a[$thiz.cx];
      $p_sci_ChampBaseReverseIterator__popNode__V($thiz);
      if (currNode.gs()) {
        $p_sci_ChampBaseReverseIterator__setupPayloadNode__sci_Node__V($thiz, currNode);
        return true;
      }
    }
  }
  return false;
}
/** @constructor */
function $c_sci_ChampBaseReverseIterator() {
  this.eA = 0;
  this.ir = null;
  this.cx = 0;
  this.h2 = null;
  this.h3 = null;
}
$p = $c_sci_ChampBaseReverseIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sci_ChampBaseReverseIterator;
/** @constructor */
function $h_sci_ChampBaseReverseIterator() {
}
$h_sci_ChampBaseReverseIterator.prototype = $p;
$p.k = (function() {
  return ((this.eA >= 0) || $p_sci_ChampBaseReverseIterator__searchNextValueNode__Z(this));
});
function $p_sci_HashMapBuilder__isAliased__Z($thiz) {
  return ($thiz.gd !== null);
}
function $p_sci_HashMapBuilder__insertElement__AI__I__I__AI($thiz, as, ix, elem) {
  if ((ix < 0)) {
    throw $ct_jl_ArrayIndexOutOfBoundsException__(new $c_jl_ArrayIndexOutOfBoundsException());
  }
  if ((ix > as.a.length)) {
    throw $ct_jl_ArrayIndexOutOfBoundsException__(new $c_jl_ArrayIndexOutOfBoundsException());
  }
  var result = new $ac_I(((1 + as.a.length) | 0));
  as.s(0, result, 0, ix);
  result.a[ix] = elem;
  var destPos = ((1 + ix) | 0);
  var length = ((as.a.length - ix) | 0);
  as.s(ix, result, destPos, length);
  return result;
}
function $p_sci_HashMapBuilder__insertValue__sci_BitmapIndexedMapNode__I__O__I__I__O__V($thiz, bm, bitpos, key, originalHash, keyHash, value) {
  var dataIx = bm.dl(bitpos);
  var idx = (dataIx << 1);
  var src = bm.b1;
  var dst = new $ac_O(((2 + src.a.length) | 0));
  src.s(0, dst, 0, idx);
  dst.a[idx] = key;
  dst.a[((1 + idx) | 0)] = value;
  var destPos = ((2 + idx) | 0);
  var length = ((src.a.length - idx) | 0);
  src.s(idx, dst, destPos, length);
  var dstHashes = $p_sci_HashMapBuilder__insertElement__AI__I__I__AI($thiz, bm.ch, dataIx, originalHash);
  bm.ac = (bm.ac | bitpos);
  bm.b1 = dst;
  bm.ch = dstHashes;
  bm.bs = ((1 + bm.bs) | 0);
  bm.c3 = ((bm.c3 + keyHash) | 0);
}
function $p_sci_HashMapBuilder__ensureUnaliased__V($thiz) {
  if ($p_sci_HashMapBuilder__isAliased__Z($thiz)) {
    $p_sci_HashMapBuilder__copyElems__V($thiz);
  }
  $thiz.gd = null;
}
function $p_sci_HashMapBuilder__copyElems__V($thiz) {
  $thiz.dB = $thiz.dB.nY();
}
/** @constructor */
function $c_sci_HashMapBuilder() {
  this.gd = null;
  this.dB = null;
  this.dB = new $c_sci_BitmapIndexedMapNode(0, 0, $m_s_Array$EmptyArrays$().jA, $m_s_Array$EmptyArrays$().gO, 0, 0);
}
$p = $c_sci_HashMapBuilder.prototype = new $h_O();
$p.constructor = $c_sci_HashMapBuilder;
/** @constructor */
function $h_sci_HashMapBuilder() {
}
$h_sci_HashMapBuilder.prototype = $p;
$p.aZ = (function(size) {
});
$p.gC = (function(mapNode, key, value, originalHash, keyHash, shift) {
  if ((mapNode instanceof $c_sci_BitmapIndexedMapNode)) {
    var mask = $m_sci_Node$().cH(keyHash, shift);
    var bitpos = $m_sci_Node$().bZ(mask);
    if (((mapNode.ac & bitpos) !== 0)) {
      var index = $m_sci_Node$().bL(mapNode.ac, mask, bitpos);
      var key0 = mapNode.dP(index);
      var key0UnimprovedHash = mapNode.co(index);
      if (((key0UnimprovedHash === originalHash) && $m_sr_BoxesRunTime$().n(key0, key))) {
        mapNode.b1.a[((1 + (index << 1)) | 0)] = value;
        return (void 0);
      } else {
        var value0 = mapNode.dp(index);
        var key0Hash = $m_sc_Hashing$().bD(key0UnimprovedHash);
        var subNodeNew = mapNode.l1(key0, value0, key0UnimprovedHash, key0Hash, key, value, originalHash, keyHash, ((5 + shift) | 0));
        mapNode.rw(bitpos, key0Hash, subNodeNew);
        return (void 0);
      }
    } else if (((mapNode.az & bitpos) !== 0)) {
      var index$2 = $m_sci_Node$().bL(mapNode.az, mask, bitpos);
      var subNode = mapNode.dn(index$2);
      var beforeSize = subNode.M();
      var beforeHash = subNode.bS();
      this.gC(subNode, key, value, originalHash, keyHash, ((5 + shift) | 0));
      mapNode.bs = ((mapNode.bs + ((subNode.M() - beforeSize) | 0)) | 0);
      mapNode.c3 = ((mapNode.c3 + ((subNode.bS() - beforeHash) | 0)) | 0);
      return (void 0);
    } else {
      $p_sci_HashMapBuilder__insertValue__sci_BitmapIndexedMapNode__I__O__I__I__O__V(this, mapNode, bitpos, key, originalHash, keyHash, value);
      return (void 0);
    }
  }
  if ((mapNode instanceof $c_sci_HashCollisionMapNode)) {
    var index$3 = mapNode.gu(key);
    if ((index$3 < 0)) {
      mapNode.aP = mapNode.aP.dM($ct_T2__O__O__(new $c_T2(), key, value));
      return (void 0);
    } else {
      mapNode.aP = mapNode.aP.er(index$3, $ct_T2__O__O__(new $c_T2(), key, value));
      return (void 0);
    }
  }
  throw new $c_s_MatchError(mapNode);
});
$p.l3 = (function() {
  if ((this.dB.bs === 0)) {
    return $m_sci_HashMap$().h4;
  } else if ((this.gd !== null)) {
    return this.gd;
  } else {
    this.gd = new $c_sci_HashMap(this.dB);
    return this.gd;
  }
});
$p.nM = (function(elem) {
  $p_sci_HashMapBuilder__ensureUnaliased__V(this);
  var h = $m_sr_Statics$().y(elem.aw());
  var im = $m_sc_Hashing$().bD(h);
  this.gC(this.dB, elem.aw(), elem.ap(), h, im, 0);
  return this;
});
$p.fA = (function(key, value) {
  $p_sci_HashMapBuilder__ensureUnaliased__V(this);
  var originalHash = $m_sr_Statics$().y(key);
  this.gC(this.dB, key, value, originalHash, $m_sc_Hashing$().bD(originalHash), 0);
  return this;
});
$p.kl = (function(xs) {
  $p_sci_HashMapBuilder__ensureUnaliased__V(this);
  if ((xs instanceof $c_sci_HashMap)) {
    new $c_sci_HashMapBuilder$$anon$1(xs, this);
  } else if ((xs instanceof $c_scm_HashMap)) {
    var iter = xs.oB();
    while (iter.k()) {
      var next = iter.f();
      var improvedHash = next.dK;
      var originalHash = (improvedHash ^ ((improvedHash >>> 16) | 0));
      var hash = $m_sc_Hashing$().bD(originalHash);
      this.gC(this.dB, next.eT, next.cC, originalHash, hash, 0);
    }
  } else if (false) {
    var iter$2 = xs.oa();
    while (iter$2.k()) {
      var next$2 = iter$2.f();
      var originalHash$2 = xs.sD(next$2.or());
      var hash$2 = $m_sc_Hashing$().bD(originalHash$2);
      this.gC(this.dB, next$2.ox(), next$2.p7(), originalHash$2, hash$2, 0);
    }
  } else if ($is_sci_Map(xs)) {
    xs.dm(new $c_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8(((key$2, value$2) => this.fA(key$2, value$2))));
  } else {
    var it = xs.c();
    while (it.k()) {
      this.nM(it.f());
    }
  }
  return this;
});
$p.aU = (function() {
  return this.l3();
});
$p.aq = (function(elem) {
  return this.nM(elem);
});
$p.b7 = (function(elems) {
  return this.kl(elems);
});
var $d_sci_HashMapBuilder = new $TypeData().i($c_sci_HashMapBuilder, "scala.collection.immutable.HashMapBuilder", ({
  fF: 1,
  H: 1,
  I: 1,
  K: 1,
  a7: 1
}));
function $p_sci_HashSetBuilder__isAliased__Z($thiz) {
  return ($thiz.fj !== null);
}
function $p_sci_HashSetBuilder__insertElement__AI__I__I__AI($thiz, as, ix, elem) {
  if ((ix < 0)) {
    throw $ct_jl_ArrayIndexOutOfBoundsException__(new $c_jl_ArrayIndexOutOfBoundsException());
  }
  if ((ix > as.a.length)) {
    throw $ct_jl_ArrayIndexOutOfBoundsException__(new $c_jl_ArrayIndexOutOfBoundsException());
  }
  var result = new $ac_I(((1 + as.a.length) | 0));
  as.s(0, result, 0, ix);
  result.a[ix] = elem;
  var destPos = ((1 + ix) | 0);
  var length = ((as.a.length - ix) | 0);
  as.s(ix, result, destPos, length);
  return result;
}
function $p_sci_HashSetBuilder__insertValue__sci_BitmapIndexedSetNode__I__O__I__I__V($thiz, bm, bitpos, key, originalHash, keyHash) {
  var dataIx = bm.dl(bitpos);
  var src = bm.bm;
  var dst = new $ac_O(((1 + src.a.length) | 0));
  src.s(0, dst, 0, dataIx);
  dst.a[dataIx] = key;
  var destPos = ((1 + dataIx) | 0);
  var length = ((src.a.length - dataIx) | 0);
  src.s(dataIx, dst, destPos, length);
  var dstHashes = $p_sci_HashSetBuilder__insertElement__AI__I__I__AI($thiz, bm.cw, dataIx, originalHash);
  bm.a5 = (bm.a5 | bitpos);
  bm.bm = dst;
  bm.cw = dstHashes;
  bm.bt = ((1 + bm.bt) | 0);
  bm.ci = ((bm.ci + keyHash) | 0);
}
function $p_sci_HashSetBuilder__setValue__sci_BitmapIndexedSetNode__I__O__V($thiz, bm, bitpos, elem) {
  var dataIx = bm.dl(bitpos);
  bm.bm.a[dataIx] = elem;
}
function $p_sci_HashSetBuilder__ensureUnaliased__V($thiz) {
  if ($p_sci_HashSetBuilder__isAliased__Z($thiz)) {
    $p_sci_HashSetBuilder__copyElems__V($thiz);
  }
  $thiz.fj = null;
}
function $p_sci_HashSetBuilder__copyElems__V($thiz) {
  $thiz.fk = $thiz.fk.nZ();
}
/** @constructor */
function $c_sci_HashSetBuilder() {
  this.fj = null;
  this.fk = null;
  this.fj = null;
  this.fk = new $c_sci_BitmapIndexedSetNode(0, 0, $m_s_Array$EmptyArrays$().jA, $m_s_Array$EmptyArrays$().gO, 0, 0);
}
$p = $c_sci_HashSetBuilder.prototype = new $h_O();
$p.constructor = $c_sci_HashSetBuilder;
/** @constructor */
function $h_sci_HashSetBuilder() {
}
$h_sci_HashSetBuilder.prototype = $p;
$p.aZ = (function(size) {
});
$p.lc = (function(setNode, element, originalHash, elementHash, shift) {
  if ((setNode instanceof $c_sci_BitmapIndexedSetNode)) {
    var mask = $m_sci_Node$().cH(elementHash, shift);
    var bitpos = $m_sci_Node$().bZ(mask);
    if (((setNode.a5 & bitpos) !== 0)) {
      var index = $m_sci_Node$().bL(setNode.a5, mask, bitpos);
      var element0 = setNode.dR(index);
      var element0UnimprovedHash = setNode.co(index);
      if (((element0UnimprovedHash === originalHash) && $m_sr_BoxesRunTime$().n(element0, element))) {
        $p_sci_HashSetBuilder__setValue__sci_BitmapIndexedSetNode__I__O__V(this, setNode, bitpos, element0);
        return (void 0);
      } else {
        var element0Hash = $m_sc_Hashing$().bD(element0UnimprovedHash);
        var subNodeNew = setNode.l0(element0, element0UnimprovedHash, element0Hash, element, originalHash, elementHash, ((5 + shift) | 0));
        setNode.rx(bitpos, element0Hash, subNodeNew);
        return (void 0);
      }
    } else if (((setNode.aO & bitpos) !== 0)) {
      var index$2 = $m_sci_Node$().bL(setNode.aO, mask, bitpos);
      var subNode = setNode.dQ(index$2);
      var beforeSize = subNode.M();
      var beforeHashCode = subNode.bS();
      this.lc(subNode, element, originalHash, elementHash, ((5 + shift) | 0));
      setNode.bt = ((setNode.bt + ((subNode.M() - beforeSize) | 0)) | 0);
      setNode.ci = ((setNode.ci + ((subNode.bS() - beforeHashCode) | 0)) | 0);
      return (void 0);
    } else {
      $p_sci_HashSetBuilder__insertValue__sci_BitmapIndexedSetNode__I__O__I__I__V(this, setNode, bitpos, element, originalHash, elementHash);
      return (void 0);
    }
  }
  if ((setNode instanceof $c_sci_HashCollisionSetNode)) {
    var index$3 = $f_sc_SeqOps__indexOf__O__I__I(setNode.b2, element, 0);
    if ((index$3 < 0)) {
      setNode.b2 = setNode.b2.dM(element);
      return (void 0);
    } else {
      setNode.b2 = setNode.b2.er(index$3, element);
      return (void 0);
    }
  }
  throw new $c_s_MatchError(setNode);
});
$p.l4 = (function() {
  if ((this.fk.bt === 0)) {
    return $m_sci_HashSet$().iu;
  } else if ((this.fj !== null)) {
    return this.fj;
  } else {
    this.fj = new $c_sci_HashSet(this.fk);
    return this.fj;
  }
});
$p.iO = (function(elem) {
  $p_sci_HashSetBuilder__ensureUnaliased__V(this);
  var h = $m_sr_Statics$().y(elem);
  var im = $m_sc_Hashing$().bD(h);
  this.lc(this.fk, elem, h, im, 0);
  return this;
});
$p.km = (function(xs) {
  $p_sci_HashSetBuilder__ensureUnaliased__V(this);
  if ((xs instanceof $c_sci_HashSet)) {
    new $c_sci_HashSetBuilder$$anon$1(xs, this);
  } else {
    var it = xs.c();
    while (it.k()) {
      this.iO(it.f());
    }
  }
  return this;
});
$p.aU = (function() {
  return this.l4();
});
$p.aq = (function(elem) {
  return this.iO(elem);
});
$p.b7 = (function(elems) {
  return this.km(elems);
});
var $d_sci_HashSetBuilder = new $TypeData().i($c_sci_HashSetBuilder, "scala.collection.immutable.HashSetBuilder", ({
  fI: 1,
  H: 1,
  I: 1,
  K: 1,
  a7: 1
}));
/** @constructor */
function $c_sci_IndexedSeq$() {
  this.e5 = null;
  $ct_sc_SeqFactory$Delegate__sc_SeqFactory__(this, $m_sci_Vector$());
}
$p = $c_sci_IndexedSeq$.prototype = new $h_sc_SeqFactory$Delegate();
$p.constructor = $c_sci_IndexedSeq$;
/** @constructor */
function $h_sci_IndexedSeq$() {
}
$h_sci_IndexedSeq$.prototype = $p;
$p.oi = (function(it) {
  return ($is_sci_IndexedSeq(it) ? it : $c_sc_SeqFactory$Delegate.prototype.fD.call(this, it));
});
$p.fD = (function(it) {
  return this.oi(it);
});
$p.a3 = (function(source) {
  return this.oi(source);
});
var $d_sci_IndexedSeq$ = new $TypeData().i($c_sci_IndexedSeq$, "scala.collection.immutable.IndexedSeq$", ({
  fK: 1,
  aD: 1,
  a: 1,
  B: 1,
  W: 1
}));
var $n_sci_IndexedSeq$;
function $m_sci_IndexedSeq$() {
  if ((!$n_sci_IndexedSeq$)) {
    $n_sci_IndexedSeq$ = new $c_sci_IndexedSeq$();
  }
  return $n_sci_IndexedSeq$;
}
/** @constructor */
function $c_sci_LazyList$LazyBuilder() {
  this.ge = null;
  this.mU = null;
  this.qa();
}
$p = $c_sci_LazyList$LazyBuilder.prototype = new $h_O();
$p.constructor = $c_sci_LazyList$LazyBuilder;
/** @constructor */
function $h_sci_LazyList$LazyBuilder() {
}
$h_sci_LazyList$LazyBuilder.prototype = $p;
$p.aZ = (function(size) {
});
$p.qa = (function() {
  var deferred = new $c_sci_LazyList$LazyBuilder$DeferredState();
  this.mU = ($m_sci_LazyList$(), $ct_sci_LazyList__O__(new $c_sci_LazyList(), new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => deferred.kA()))));
  this.ge = deferred;
});
$p.rQ = (function() {
  this.ge.kR(new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => $m_sci_LazyList$().ah)));
  return this.mU;
});
$p.pQ = (function(elem) {
  var deferred = new $c_sci_LazyList$LazyBuilder$DeferredState();
  this.ge.kR(new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => {
    $m_sci_LazyList$();
    return $ct_sci_LazyList__O__sci_LazyList__(new $c_sci_LazyList(), elem, ($m_sci_LazyList$(), $ct_sci_LazyList__O__(new $c_sci_LazyList(), new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => deferred.kA())))));
  })));
  this.ge = deferred;
  return this;
});
$p.pD = (function(xs) {
  if ((xs.q() !== 0)) {
    var deferred = new $c_sci_LazyList$LazyBuilder$DeferredState();
    this.ge.kR(new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => $m_sci_LazyList$().oO(xs.c(), new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => deferred.kA()))))));
    this.ge = deferred;
  }
  return this;
});
$p.aU = (function() {
  return this.rQ();
});
$p.aq = (function(elem) {
  return this.pQ(elem);
});
$p.b7 = (function(elems) {
  return this.pD(elems);
});
var $d_sci_LazyList$LazyBuilder = new $TypeData().i($c_sci_LazyList$LazyBuilder, "scala.collection.immutable.LazyList$LazyBuilder", ({
  fP: 1,
  H: 1,
  I: 1,
  K: 1,
  a7: 1
}));
/** @constructor */
function $c_sci_LazyList$LazyIterator(lazyList) {
  this.gf = null;
  this.gf = lazyList;
}
$p = $c_sci_LazyList$LazyIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sci_LazyList$LazyIterator;
/** @constructor */
function $h_sci_LazyList$LazyIterator() {
}
$h_sci_LazyList$LazyIterator.prototype = $p;
$p.k = (function() {
  return (!($p_sci_LazyList__evaluated__sci_LazyList(this.gf) === $m_sci_LazyList$().ah));
});
$p.f = (function() {
  if (($p_sci_LazyList__evaluated__sci_LazyList(this.gf) === $m_sci_LazyList$().ah)) {
    return $m_sc_Iterator$().G.f();
  } else {
    var res = this.gf.m();
    this.gf = this.gf.bI();
    return res;
  }
});
var $d_sci_LazyList$LazyIterator = new $TypeData().i($c_sci_LazyList$LazyIterator, "scala.collection.immutable.LazyList$LazyIterator", ({
  fR: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sci_List$() {
  this.pj = null;
  $n_sci_List$ = this;
  var _1 = $m_sci_Nil$();
  $m_sci_Nil$();
  this.pj = new $c_sci_List$$anon$1();
}
$p = $c_sci_List$.prototype = new $h_O();
$p.constructor = $c_sci_List$;
/** @constructor */
function $h_sci_List$() {
}
$h_sci_List$.prototype = $p;
$p.em = (function(elems) {
  return $m_sci_Nil$().cc(elems);
});
$p.T = (function() {
  return new $c_scm_ListBuffer();
});
$p.a3 = (function(source) {
  return $m_sci_Nil$().cc(source);
});
var $d_sci_List$ = new $TypeData().i($c_sci_List$, "scala.collection.immutable.List$", ({
  fU: 1,
  a: 1,
  B: 1,
  W: 1,
  at: 1
}));
var $n_sci_List$;
function $m_sci_List$() {
  if ((!$n_sci_List$)) {
    $n_sci_List$ = new $c_sci_List$();
  }
  return $n_sci_List$;
}
function $ct_sci_Map$Map2$Map2Iterator__sci_Map$Map2__($thiz, outer) {
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $thiz.gg = outer;
  $thiz.eD = 0;
  return $thiz;
}
/** @constructor */
function $c_sci_Map$Map2$Map2Iterator() {
  this.eD = 0;
  this.gg = null;
}
$p = $c_sci_Map$Map2$Map2Iterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sci_Map$Map2$Map2Iterator;
/** @constructor */
function $h_sci_Map$Map2$Map2Iterator() {
}
$h_sci_Map$Map2$Map2Iterator.prototype = $p;
$p.k = (function() {
  return (this.eD < 2);
});
$p.f = (function() {
  matchResult5$1: {
    var result;
    var x23 = this.eD;
    if ((x23 === 0)) {
      var result = $ct_T2__O__O__(new $c_T2(), this.gg.cS, this.gg.e8);
      break matchResult5$1;
    }
    if ((x23 === 1)) {
      var result = $ct_T2__O__O__(new $c_T2(), this.gg.cT, this.gg.e9);
      break matchResult5$1;
    }
    var result = $m_sc_Iterator$().G.f();
  }
  this.eD = ((1 + this.eD) | 0);
  return result;
});
$p.cE = (function(n) {
  this.eD = ((this.eD + n) | 0);
  return this;
});
function $ct_sci_Map$Map3$Map3Iterator__sci_Map$Map3__($thiz, outer) {
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $thiz.eE = outer;
  $thiz.eF = 0;
  return $thiz;
}
/** @constructor */
function $c_sci_Map$Map3$Map3Iterator() {
  this.eF = 0;
  this.eE = null;
}
$p = $c_sci_Map$Map3$Map3Iterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sci_Map$Map3$Map3Iterator;
/** @constructor */
function $h_sci_Map$Map3$Map3Iterator() {
}
$h_sci_Map$Map3$Map3Iterator.prototype = $p;
$p.k = (function() {
  return (this.eF < 3);
});
$p.f = (function() {
  var result;
  switch (this.eF) {
    case 0: {
      var result = $ct_T2__O__O__(new $c_T2(), this.eE.cy, this.eE.dD);
      break;
    }
    case 1: {
      var result = $ct_T2__O__O__(new $c_T2(), this.eE.cz, this.eE.dE);
      break;
    }
    case 2: {
      var result = $ct_T2__O__O__(new $c_T2(), this.eE.cA, this.eE.dF);
      break;
    }
    default: {
      var result = $m_sc_Iterator$().G.f();
    }
  }
  this.eF = ((1 + this.eF) | 0);
  return result;
});
$p.cE = (function(n) {
  this.eF = ((this.eF + n) | 0);
  return this;
});
function $ct_sci_Map$Map4$Map4Iterator__sci_Map$Map4__($thiz, outer) {
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $thiz.dG = outer;
  $thiz.eG = 0;
  return $thiz;
}
/** @constructor */
function $c_sci_Map$Map4$Map4Iterator() {
  this.eG = 0;
  this.dG = null;
}
$p = $c_sci_Map$Map4$Map4Iterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sci_Map$Map4$Map4Iterator;
/** @constructor */
function $h_sci_Map$Map4$Map4Iterator() {
}
$h_sci_Map$Map4$Map4Iterator.prototype = $p;
$p.k = (function() {
  return (this.eG < 4);
});
$p.f = (function() {
  var result;
  switch (this.eG) {
    case 0: {
      var result = $ct_T2__O__O__(new $c_T2(), this.dG.c5, this.dG.cU);
      break;
    }
    case 1: {
      var result = $ct_T2__O__O__(new $c_T2(), this.dG.c6, this.dG.cV);
      break;
    }
    case 2: {
      var result = $ct_T2__O__O__(new $c_T2(), this.dG.c7, this.dG.cW);
      break;
    }
    case 3: {
      var result = $ct_T2__O__O__(new $c_T2(), this.dG.c8, this.dG.cX);
      break;
    }
    default: {
      var result = $m_sc_Iterator$().G.f();
    }
  }
  this.eG = ((1 + this.eG) | 0);
  return result;
});
$p.cE = (function(n) {
  this.eG = ((this.eG + n) | 0);
  return this;
});
/** @constructor */
function $c_sci_MapBuilderImpl() {
  this.ea = null;
  this.h5 = false;
  this.fl = null;
  this.ea = $m_sci_Map$EmptyMap$();
  this.h5 = false;
}
$p = $c_sci_MapBuilderImpl.prototype = new $h_O();
$p.constructor = $c_sci_MapBuilderImpl;
/** @constructor */
function $h_sci_MapBuilderImpl() {
}
$h_sci_MapBuilderImpl.prototype = $p;
$p.aZ = (function(size) {
});
$p.oH = (function() {
  return (this.h5 ? this.fl.l3() : this.ea);
});
$p.pO = (function(key, value) {
  if (this.h5) {
    this.fl.fA(key, value);
  } else if ((this.ea.M() < 4)) {
    this.ea = this.ea.fO(key, value);
  } else if (this.ea.bb(key)) {
    this.ea = this.ea.fO(key, value);
  } else {
    this.h5 = true;
    if ((this.fl === null)) {
      this.fl = new $c_sci_HashMapBuilder();
    }
    this.ea.q8(this.fl);
    this.fl.fA(key, value);
  }
  return this;
});
$p.nF = (function(xs) {
  return (this.h5 ? (this.fl.kl(xs), this) : $f_scm_Growable__addAll__sc_IterableOnce__scm_Growable(this, xs));
});
$p.aU = (function() {
  return this.oH();
});
$p.aq = (function(elem) {
  return this.pO(elem.aw(), elem.ap());
});
$p.b7 = (function(elems) {
  return this.nF(elems);
});
var $d_sci_MapBuilderImpl = new $TypeData().i($c_sci_MapBuilderImpl, "scala.collection.immutable.MapBuilderImpl", ({
  g6: 1,
  H: 1,
  I: 1,
  K: 1,
  a7: 1
}));
/** @constructor */
function $c_sci_Seq$() {
  this.e5 = null;
  $ct_sc_SeqFactory$Delegate__sc_SeqFactory__(this, $m_sci_List$());
}
$p = $c_sci_Seq$.prototype = new $h_sc_SeqFactory$Delegate();
$p.constructor = $c_sci_Seq$;
/** @constructor */
function $h_sci_Seq$() {
}
$h_sci_Seq$.prototype = $p;
$p.oj = (function(it) {
  return ($is_sci_Seq(it) ? it : $c_sc_SeqFactory$Delegate.prototype.fD.call(this, it));
});
$p.fD = (function(it) {
  return this.oj(it);
});
$p.a3 = (function(source) {
  return this.oj(source);
});
var $d_sci_Seq$ = new $TypeData().i($c_sci_Seq$, "scala.collection.immutable.Seq$", ({
  ge: 1,
  aD: 1,
  a: 1,
  B: 1,
  W: 1
}));
var $n_sci_Seq$;
function $m_sci_Seq$() {
  if ((!$n_sci_Seq$)) {
    $n_sci_Seq$ = new $c_sci_Seq$();
  }
  return $n_sci_Seq$;
}
/** @constructor */
function $c_sci_SetBuilderImpl() {
  this.fu = null;
  this.h8 = false;
  this.fv = null;
  this.fu = $m_sci_Set$EmptySet$();
  this.h8 = false;
}
$p = $c_sci_SetBuilderImpl.prototype = new $h_O();
$p.constructor = $c_sci_SetBuilderImpl;
/** @constructor */
function $h_sci_SetBuilderImpl() {
}
$h_sci_SetBuilderImpl.prototype = $p;
$p.aZ = (function(size) {
});
$p.oI = (function() {
  return (this.h8 ? this.fv.l4() : this.fu);
});
$p.pR = (function(elem) {
  if (this.h8) {
    this.fv.iO(elem);
  } else if ((this.fu.M() < 4)) {
    this.fu = this.fu.dT(elem);
  } else if ((!this.fu.bb(elem))) {
    this.h8 = true;
    if ((this.fv === null)) {
      this.fv = new $c_sci_HashSetBuilder();
    }
    this.fu.q9(this.fv);
    this.fv.iO(elem);
  }
  return this;
});
$p.nG = (function(xs) {
  return (this.h8 ? (this.fv.km(xs), this) : $f_scm_Growable__addAll__sc_IterableOnce__scm_Growable(this, xs));
});
$p.aU = (function() {
  return this.oI();
});
$p.aq = (function(elem) {
  return this.pR(elem);
});
$p.b7 = (function(elems) {
  return this.nG(elems);
});
var $d_sci_SetBuilderImpl = new $TypeData().i($c_sci_SetBuilderImpl, "scala.collection.immutable.SetBuilderImpl", ({
  go: 1,
  H: 1,
  I: 1,
  K: 1,
  a7: 1
}));
/** @constructor */
function $c_sci_Vector$() {
  this.n2 = 0;
  this.n3 = null;
  $n_sci_Vector$ = this;
  try {
    $m_sc_StringOps$();
    var $x_1 = $m_jl_Integer$().kU($m_jl_System$SystemProperties$().kO("scala.collection.immutable.Vector.defaultApplyPreferredMaxLength", "250"), 10, 214748364);
  } catch (e) {
    if (false) {
      var $x_1 = 250;
    } else {
      var $x_1;
      throw e;
    }
  }
  this.n2 = $x_1;
  this.n3 = new $c_sci_NewVectorIterator($m_sci_Vector0$(), 0, 0);
}
$p = $c_sci_Vector$.prototype = new $h_O();
$p.constructor = $c_sci_Vector$;
/** @constructor */
function $h_sci_Vector$() {
}
$h_sci_Vector$.prototype = $p;
$p.em = (function(elems) {
  return this.iU(elems);
});
$p.iU = (function(it) {
  if ((it instanceof $c_sci_Vector)) {
    return it;
  } else {
    var knownSize = it.q();
    if ((knownSize === 0)) {
      return $m_sci_Vector0$();
    } else if (((((knownSize - 1) | 0) >>> 0) <= 31)) {
      matchResult3: {
        var $x_1;
        if ((it instanceof $c_sci_ArraySeq$ofRef)) {
          var x = it.aS().bk();
          if (((x !== null) && (x === $d_O.l()))) {
            var $x_1 = it.cQ;
            break matchResult3;
          }
        }
        if ($is_sci_Iterable(it)) {
          var a1 = new $ac_O(knownSize);
          it.bc(a1, 0, 2147483647);
          var $x_1 = a1;
          break matchResult3;
        }
        var a1$2 = new $ac_O(knownSize);
        it.c().bc(a1$2, 0, 2147483647);
        var $x_1 = a1$2;
      }
      return new $c_sci_Vector1($x_1);
    } else {
      return new $c_sci_VectorBuilder().nH(it).oJ();
    }
  }
});
$p.a3 = (function(source) {
  return this.iU(source);
});
$p.T = (function() {
  return new $c_sci_VectorBuilder();
});
var $d_sci_Vector$ = new $TypeData().i($c_sci_Vector$, "scala.collection.immutable.Vector$", ({
  gv: 1,
  a: 1,
  B: 1,
  W: 1,
  at: 1
}));
var $n_sci_Vector$;
function $m_sci_Vector$() {
  if ((!$n_sci_Vector$)) {
    $n_sci_Vector$ = new $c_sci_Vector$();
  }
  return $n_sci_Vector$;
}
function $p_sci_VectorBuilder__leftAlignPrefix__V($thiz) {
  var a = null;
  var aParent = null;
  if (($thiz.P >= 6)) {
    a = $thiz.bg;
    var i = (($thiz.L >>> 25) | 0);
    if ((i > 0)) {
      var src = a;
      var dest = a;
      var length = ((64 - i) | 0);
      src.s(i, dest, 0, length);
    }
    var num = $thiz.L;
    var t = (((num >> 24) >>> 7) | 0);
    var newOffset = (((33554431 & ((num + t) | 0)) - t) | 0);
    $thiz.C = (($thiz.C - (($thiz.L - newOffset) | 0)) | 0);
    $thiz.L = newOffset;
    if (((($thiz.C >>> 25) | 0) === 0)) {
      $thiz.P = 5;
    }
    aParent = a;
    a = a.a[0];
  }
  if (($thiz.P >= 5)) {
    if ((a === null)) {
      a = $thiz.au;
    }
    var i$2 = (31 & (($thiz.L >>> 20) | 0));
    if (($thiz.P === 5)) {
      if ((i$2 > 0)) {
        var src$1 = a;
        var dest$1 = a;
        var length$1 = ((32 - i$2) | 0);
        src$1.s(i$2, dest$1, 0, length$1);
      }
      $thiz.au = a;
      var num$1 = $thiz.L;
      var t$1 = (((num$1 >> 19) >>> 12) | 0);
      var newOffset$1 = (((1048575 & ((num$1 + t$1) | 0)) - t$1) | 0);
      $thiz.C = (($thiz.C - (($thiz.L - newOffset$1) | 0)) | 0);
      $thiz.L = newOffset$1;
      if (((($thiz.C >>> 20) | 0) === 0)) {
        $thiz.P = 4;
      }
    } else {
      if ((i$2 > 0)) {
        a = $m_ju_Arrays$().aN(a, i$2, 32);
      }
      aParent.a[0] = a;
    }
    aParent = a;
    a = a.a[0];
  }
  if (($thiz.P >= 4)) {
    if ((a === null)) {
      a = $thiz.a1;
    }
    var i$3 = (31 & (($thiz.L >>> 15) | 0));
    if (($thiz.P === 4)) {
      if ((i$3 > 0)) {
        var src$2 = a;
        var dest$2 = a;
        var length$2 = ((32 - i$3) | 0);
        src$2.s(i$3, dest$2, 0, length$2);
      }
      $thiz.a1 = a;
      var num$2 = $thiz.L;
      var t$2 = (((num$2 >> 14) >>> 17) | 0);
      var newOffset$2 = (((32767 & ((num$2 + t$2) | 0)) - t$2) | 0);
      $thiz.C = (($thiz.C - (($thiz.L - newOffset$2) | 0)) | 0);
      $thiz.L = newOffset$2;
      if (((($thiz.C >>> 15) | 0) === 0)) {
        $thiz.P = 3;
      }
    } else {
      if ((i$3 > 0)) {
        a = $m_ju_Arrays$().aN(a, i$3, 32);
      }
      aParent.a[0] = a;
    }
    aParent = a;
    a = a.a[0];
  }
  if (($thiz.P >= 3)) {
    if ((a === null)) {
      a = $thiz.N;
    }
    var i$4 = (31 & (($thiz.L >>> 10) | 0));
    if (($thiz.P === 3)) {
      if ((i$4 > 0)) {
        var src$3 = a;
        var dest$3 = a;
        var length$3 = ((32 - i$4) | 0);
        src$3.s(i$4, dest$3, 0, length$3);
      }
      $thiz.N = a;
      var num$3 = $thiz.L;
      var t$3 = (((num$3 >> 9) >>> 22) | 0);
      var newOffset$3 = (((1023 & ((num$3 + t$3) | 0)) - t$3) | 0);
      $thiz.C = (($thiz.C - (($thiz.L - newOffset$3) | 0)) | 0);
      $thiz.L = newOffset$3;
      if (((($thiz.C >>> 10) | 0) === 0)) {
        $thiz.P = 2;
      }
    } else {
      if ((i$4 > 0)) {
        a = $m_ju_Arrays$().aN(a, i$4, 32);
      }
      aParent.a[0] = a;
    }
    aParent = a;
    a = a.a[0];
  }
  if (($thiz.P >= 2)) {
    if ((a === null)) {
      a = $thiz.H;
    }
    var i$5 = (31 & (($thiz.L >>> 5) | 0));
    if (($thiz.P === 2)) {
      if ((i$5 > 0)) {
        var src$4 = a;
        var dest$4 = a;
        var length$4 = ((32 - i$5) | 0);
        src$4.s(i$5, dest$4, 0, length$4);
      }
      $thiz.H = a;
      var num$4 = $thiz.L;
      var t$4 = (((num$4 >> 4) >>> 27) | 0);
      var newOffset$4 = (((31 & ((num$4 + t$4) | 0)) - t$4) | 0);
      $thiz.C = (($thiz.C - (($thiz.L - newOffset$4) | 0)) | 0);
      $thiz.L = newOffset$4;
      if (((($thiz.C >>> 5) | 0) === 0)) {
        $thiz.P = 1;
      }
    } else {
      if ((i$5 > 0)) {
        a = $m_ju_Arrays$().aN(a, i$5, 32);
      }
      aParent.a[0] = a;
    }
    aParent = a;
    a = a.a[0];
  }
  if (($thiz.P >= 1)) {
    if ((a === null)) {
      a = $thiz.af;
    }
    var i$6 = (31 & $thiz.L);
    if (($thiz.P === 1)) {
      if ((i$6 > 0)) {
        var src$5 = a;
        var dest$5 = a;
        var length$5 = ((32 - i$6) | 0);
        src$5.s(i$6, dest$5, 0, length$5);
      }
      $thiz.af = a;
      $thiz.O = (($thiz.O - $thiz.L) | 0);
      $thiz.L = 0;
    } else {
      if ((i$6 > 0)) {
        a = $m_ju_Arrays$().aN(a, i$6, 32);
      }
      aParent.a[0] = a;
    }
  }
  $thiz.iv = false;
}
function $p_sci_VectorBuilder__addArr1__AO__V($thiz, data) {
  var dl = data.a.length;
  if ((dl > 0)) {
    if (($thiz.O === 32)) {
      $p_sci_VectorBuilder__advance__V($thiz);
    }
    var a = ((32 - $thiz.O) | 0);
    var copy1 = ((a < dl) ? a : dl);
    var copy2 = ((dl - copy1) | 0);
    var dest = $thiz.af;
    var destPos = $thiz.O;
    data.s(0, dest, destPos, copy1);
    $thiz.O = (($thiz.O + copy1) | 0);
    if ((copy2 > 0)) {
      $p_sci_VectorBuilder__advance__V($thiz);
      var dest$1 = $thiz.af;
      data.s(copy1, dest$1, 0, copy2);
      $thiz.O = (($thiz.O + copy2) | 0);
    }
  }
}
function $p_sci_VectorBuilder__addArrN__AO__I__V($thiz, slice, dim) {
  if ((slice.a.length === 0)) {
    return (void 0);
  }
  if (($thiz.O === 32)) {
    $p_sci_VectorBuilder__advance__V($thiz);
  }
  var sl = slice.a.length;
  switch (dim) {
    case 2: {
      var a = (31 & ((((1024 - $thiz.C) | 0) >>> 5) | 0));
      var copy1 = ((a < sl) ? a : sl);
      var copy2 = ((sl - copy1) | 0);
      var destPos = (31 & (($thiz.C >>> 5) | 0));
      var dest = $thiz.H;
      slice.s(0, dest, destPos, copy1);
      $p_sci_VectorBuilder__advanceN__I__V($thiz, (copy1 << 5));
      if ((copy2 > 0)) {
        var dest$1 = $thiz.H;
        slice.s(copy1, dest$1, 0, copy2);
        $p_sci_VectorBuilder__advanceN__I__V($thiz, (copy2 << 5));
      }
      break;
    }
    case 3: {
      var num = $thiz.C;
      var t = (((num >> 9) >>> 22) | 0);
      if (((((1023 & ((num + t) | 0)) - t) | 0) !== 0)) {
        var f = ((e$3) => {
          $p_sci_VectorBuilder__addArrN__AO__I__V($thiz, e$3, 2);
        });
        var len = slice.a.length;
        var i = 0;
        if ((slice !== null)) {
          while ((i < len)) {
            var x0 = slice.a[i];
            f(x0);
            i = ((1 + i) | 0);
          }
        } else if ((slice instanceof $ac_I)) {
          while ((i < len)) {
            var x0$1 = slice.a[i];
            f(x0$1);
            i = ((1 + i) | 0);
          }
        } else if ((slice instanceof $ac_D)) {
          while ((i < len)) {
            var x0$2 = slice.a[i];
            f(x0$2);
            i = ((1 + i) | 0);
          }
        } else if ((slice instanceof $ac_J)) {
          while ((i < len)) {
            var $x_1 = slice.a;
            var $x_2 = (i << 1);
            var x0$3_$_lo = $x_1[$x_2];
            var x0$3_$_hi = $x_1[(($x_2 + 1) | 0)];
            f($bL(x0$3_$_lo, x0$3_$_hi));
            i = ((1 + i) | 0);
          }
        } else if ((slice instanceof $ac_F)) {
          while ((i < len)) {
            var x0$4 = slice.a[i];
            f(x0$4);
            i = ((1 + i) | 0);
          }
        } else if ((slice instanceof $ac_C)) {
          while ((i < len)) {
            var x0$5 = slice.a[i];
            f($bC(x0$5));
            i = ((1 + i) | 0);
          }
        } else if ((slice instanceof $ac_B)) {
          while ((i < len)) {
            var x0$6 = slice.a[i];
            f(x0$6);
            i = ((1 + i) | 0);
          }
        } else if ((slice instanceof $ac_S)) {
          while ((i < len)) {
            var x0$7 = slice.a[i];
            f(x0$7);
            i = ((1 + i) | 0);
          }
        } else if ((slice instanceof $ac_Z)) {
          while ((i < len)) {
            var x0$8 = slice.a[i];
            f(x0$8);
            i = ((1 + i) | 0);
          }
        } else {
          throw new $c_s_MatchError(slice);
        }
        return (void 0);
      }
      var a$1 = (31 & ((((32768 - $thiz.C) | 0) >>> 10) | 0));
      var copy1$2 = ((a$1 < sl) ? a$1 : sl);
      var copy2$2 = ((sl - copy1$2) | 0);
      var destPos$2 = (31 & (($thiz.C >>> 10) | 0));
      var dest$2 = $thiz.N;
      slice.s(0, dest$2, destPos$2, copy1$2);
      $p_sci_VectorBuilder__advanceN__I__V($thiz, (copy1$2 << 10));
      if ((copy2$2 > 0)) {
        var dest$3 = $thiz.N;
        slice.s(copy1$2, dest$3, 0, copy2$2);
        $p_sci_VectorBuilder__advanceN__I__V($thiz, (copy2$2 << 10));
      }
      break;
    }
    case 4: {
      var num$1 = $thiz.C;
      var t$1 = (((num$1 >> 14) >>> 17) | 0);
      if (((((32767 & ((num$1 + t$1) | 0)) - t$1) | 0) !== 0)) {
        var f$1 = ((e$3$1) => {
          $p_sci_VectorBuilder__addArrN__AO__I__V($thiz, e$3$1, 3);
        });
        var len$1 = slice.a.length;
        var i$1 = 0;
        if ((slice !== null)) {
          while ((i$1 < len$1)) {
            var x0$9 = slice.a[i$1];
            f$1(x0$9);
            i$1 = ((1 + i$1) | 0);
          }
        } else if ((slice instanceof $ac_I)) {
          while ((i$1 < len$1)) {
            var x0$10 = slice.a[i$1];
            f$1(x0$10);
            i$1 = ((1 + i$1) | 0);
          }
        } else if ((slice instanceof $ac_D)) {
          while ((i$1 < len$1)) {
            var x0$11 = slice.a[i$1];
            f$1(x0$11);
            i$1 = ((1 + i$1) | 0);
          }
        } else if ((slice instanceof $ac_J)) {
          while ((i$1 < len$1)) {
            var $x_3 = slice.a;
            var $x_4 = (i$1 << 1);
            var x0$12_$_lo = $x_3[$x_4];
            var x0$12_$_hi = $x_3[(($x_4 + 1) | 0)];
            f$1($bL(x0$12_$_lo, x0$12_$_hi));
            i$1 = ((1 + i$1) | 0);
          }
        } else if ((slice instanceof $ac_F)) {
          while ((i$1 < len$1)) {
            var x0$13 = slice.a[i$1];
            f$1(x0$13);
            i$1 = ((1 + i$1) | 0);
          }
        } else if ((slice instanceof $ac_C)) {
          while ((i$1 < len$1)) {
            var x0$14 = slice.a[i$1];
            f$1($bC(x0$14));
            i$1 = ((1 + i$1) | 0);
          }
        } else if ((slice instanceof $ac_B)) {
          while ((i$1 < len$1)) {
            var x0$15 = slice.a[i$1];
            f$1(x0$15);
            i$1 = ((1 + i$1) | 0);
          }
        } else if ((slice instanceof $ac_S)) {
          while ((i$1 < len$1)) {
            var x0$16 = slice.a[i$1];
            f$1(x0$16);
            i$1 = ((1 + i$1) | 0);
          }
        } else if ((slice instanceof $ac_Z)) {
          while ((i$1 < len$1)) {
            var x0$17 = slice.a[i$1];
            f$1(x0$17);
            i$1 = ((1 + i$1) | 0);
          }
        } else {
          throw new $c_s_MatchError(slice);
        }
        return (void 0);
      }
      var a$2 = (31 & ((((1048576 - $thiz.C) | 0) >>> 15) | 0));
      var copy1$3 = ((a$2 < sl) ? a$2 : sl);
      var copy2$3 = ((sl - copy1$3) | 0);
      var destPos$3 = (31 & (($thiz.C >>> 15) | 0));
      var dest$4 = $thiz.a1;
      slice.s(0, dest$4, destPos$3, copy1$3);
      $p_sci_VectorBuilder__advanceN__I__V($thiz, (copy1$3 << 15));
      if ((copy2$3 > 0)) {
        var dest$5 = $thiz.a1;
        slice.s(copy1$3, dest$5, 0, copy2$3);
        $p_sci_VectorBuilder__advanceN__I__V($thiz, (copy2$3 << 15));
      }
      break;
    }
    case 5: {
      var num$2 = $thiz.C;
      var t$2 = (((num$2 >> 19) >>> 12) | 0);
      if (((((1048575 & ((num$2 + t$2) | 0)) - t$2) | 0) !== 0)) {
        var f$2 = ((e$3$2) => {
          $p_sci_VectorBuilder__addArrN__AO__I__V($thiz, e$3$2, 4);
        });
        var len$2 = slice.a.length;
        var i$2 = 0;
        if ((slice !== null)) {
          while ((i$2 < len$2)) {
            var x0$18 = slice.a[i$2];
            f$2(x0$18);
            i$2 = ((1 + i$2) | 0);
          }
        } else if ((slice instanceof $ac_I)) {
          while ((i$2 < len$2)) {
            var x0$19 = slice.a[i$2];
            f$2(x0$19);
            i$2 = ((1 + i$2) | 0);
          }
        } else if ((slice instanceof $ac_D)) {
          while ((i$2 < len$2)) {
            var x0$20 = slice.a[i$2];
            f$2(x0$20);
            i$2 = ((1 + i$2) | 0);
          }
        } else if ((slice instanceof $ac_J)) {
          while ((i$2 < len$2)) {
            var $x_5 = slice.a;
            var $x_6 = (i$2 << 1);
            var x0$21_$_lo = $x_5[$x_6];
            var x0$21_$_hi = $x_5[(($x_6 + 1) | 0)];
            f$2($bL(x0$21_$_lo, x0$21_$_hi));
            i$2 = ((1 + i$2) | 0);
          }
        } else if ((slice instanceof $ac_F)) {
          while ((i$2 < len$2)) {
            var x0$22 = slice.a[i$2];
            f$2(x0$22);
            i$2 = ((1 + i$2) | 0);
          }
        } else if ((slice instanceof $ac_C)) {
          while ((i$2 < len$2)) {
            var x0$23 = slice.a[i$2];
            f$2($bC(x0$23));
            i$2 = ((1 + i$2) | 0);
          }
        } else if ((slice instanceof $ac_B)) {
          while ((i$2 < len$2)) {
            var x0$24 = slice.a[i$2];
            f$2(x0$24);
            i$2 = ((1 + i$2) | 0);
          }
        } else if ((slice instanceof $ac_S)) {
          while ((i$2 < len$2)) {
            var x0$25 = slice.a[i$2];
            f$2(x0$25);
            i$2 = ((1 + i$2) | 0);
          }
        } else if ((slice instanceof $ac_Z)) {
          while ((i$2 < len$2)) {
            var x0$26 = slice.a[i$2];
            f$2(x0$26);
            i$2 = ((1 + i$2) | 0);
          }
        } else {
          throw new $c_s_MatchError(slice);
        }
        return (void 0);
      }
      var a$3 = (31 & ((((33554432 - $thiz.C) | 0) >>> 20) | 0));
      var copy1$4 = ((a$3 < sl) ? a$3 : sl);
      var copy2$4 = ((sl - copy1$4) | 0);
      var destPos$4 = (31 & (($thiz.C >>> 20) | 0));
      var dest$6 = $thiz.au;
      slice.s(0, dest$6, destPos$4, copy1$4);
      $p_sci_VectorBuilder__advanceN__I__V($thiz, (copy1$4 << 20));
      if ((copy2$4 > 0)) {
        var dest$7 = $thiz.au;
        slice.s(copy1$4, dest$7, 0, copy2$4);
        $p_sci_VectorBuilder__advanceN__I__V($thiz, (copy2$4 << 20));
      }
      break;
    }
    case 6: {
      var num$3 = $thiz.C;
      var t$3 = (((num$3 >> 24) >>> 7) | 0);
      if (((((33554431 & ((num$3 + t$3) | 0)) - t$3) | 0) !== 0)) {
        var f$3 = ((e$3$3) => {
          $p_sci_VectorBuilder__addArrN__AO__I__V($thiz, e$3$3, 5);
        });
        var len$3 = slice.a.length;
        var i$3 = 0;
        if ((slice !== null)) {
          while ((i$3 < len$3)) {
            var x0$27 = slice.a[i$3];
            f$3(x0$27);
            i$3 = ((1 + i$3) | 0);
          }
        } else if ((slice instanceof $ac_I)) {
          while ((i$3 < len$3)) {
            var x0$28 = slice.a[i$3];
            f$3(x0$28);
            i$3 = ((1 + i$3) | 0);
          }
        } else if ((slice instanceof $ac_D)) {
          while ((i$3 < len$3)) {
            var x0$29 = slice.a[i$3];
            f$3(x0$29);
            i$3 = ((1 + i$3) | 0);
          }
        } else if ((slice instanceof $ac_J)) {
          while ((i$3 < len$3)) {
            var $x_7 = slice.a;
            var $x_8 = (i$3 << 1);
            var x0$30_$_lo = $x_7[$x_8];
            var x0$30_$_hi = $x_7[(($x_8 + 1) | 0)];
            f$3($bL(x0$30_$_lo, x0$30_$_hi));
            i$3 = ((1 + i$3) | 0);
          }
        } else if ((slice instanceof $ac_F)) {
          while ((i$3 < len$3)) {
            var x0$31 = slice.a[i$3];
            f$3(x0$31);
            i$3 = ((1 + i$3) | 0);
          }
        } else if ((slice instanceof $ac_C)) {
          while ((i$3 < len$3)) {
            var x0$32 = slice.a[i$3];
            f$3($bC(x0$32));
            i$3 = ((1 + i$3) | 0);
          }
        } else if ((slice instanceof $ac_B)) {
          while ((i$3 < len$3)) {
            var x0$33 = slice.a[i$3];
            f$3(x0$33);
            i$3 = ((1 + i$3) | 0);
          }
        } else if ((slice instanceof $ac_S)) {
          while ((i$3 < len$3)) {
            var x0$34 = slice.a[i$3];
            f$3(x0$34);
            i$3 = ((1 + i$3) | 0);
          }
        } else if ((slice instanceof $ac_Z)) {
          while ((i$3 < len$3)) {
            var x0$35 = slice.a[i$3];
            f$3(x0$35);
            i$3 = ((1 + i$3) | 0);
          }
        } else {
          throw new $c_s_MatchError(slice);
        }
        return (void 0);
      }
      var destPos$5 = (($thiz.C >>> 25) | 0);
      if ((((destPos$5 + sl) | 0) > 64)) {
        throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), "exceeding 2^31 elements");
      }
      var dest$8 = $thiz.bg;
      slice.s(0, dest$8, destPos$5, sl);
      $p_sci_VectorBuilder__advanceN__I__V($thiz, (sl << 25));
      break;
    }
    default: {
      throw new $c_s_MatchError(dim);
    }
  }
}
function $p_sci_VectorBuilder__addVector__sci_Vector__sci_VectorBuilder($thiz, xs) {
  var sliceCount = xs.dY();
  var sliceIdx = 0;
  while ((sliceIdx < sliceCount)) {
    var slice = xs.dX(sliceIdx);
    matchResult26: {
      var idx = sliceIdx;
      var c = (((sliceCount + ((sliceCount >>> 31) | 0)) | 0) >> 1);
      var a = ((idx - c) | 0);
      var sign = (a >> 31);
      var x37 = ((((1 + c) | 0) - (((a ^ sign) - sign) | 0)) | 0);
      if ((x37 === 1)) {
        $p_sci_VectorBuilder__addArr1__AO__V($thiz, slice);
        break matchResult26;
      }
      if ((($thiz.O === 32) || ($thiz.O === 0))) {
        $p_sci_VectorBuilder__addArrN__AO__I__V($thiz, slice, x37);
        break matchResult26;
      }
      $m_sci_VectorStatics$().kE(((x37 - 2) | 0), slice, new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((data$3) => {
        $p_sci_VectorBuilder__addArr1__AO__V($thiz, data$3);
      })));
    }
    sliceIdx = ((1 + sliceIdx) | 0);
  }
  return $thiz;
}
function $p_sci_VectorBuilder__advance__V($thiz) {
  var idx = ((32 + $thiz.C) | 0);
  var xor = (idx ^ $thiz.C);
  $thiz.C = idx;
  $thiz.O = 0;
  $p_sci_VectorBuilder__advance1__I__I__V($thiz, idx, xor);
}
function $p_sci_VectorBuilder__advanceN__I__V($thiz, n) {
  if ((n > 0)) {
    var idx = (($thiz.C + n) | 0);
    var xor = (idx ^ $thiz.C);
    $thiz.C = idx;
    $thiz.O = 0;
    $p_sci_VectorBuilder__advance1__I__I__V($thiz, idx, xor);
  }
}
function $p_sci_VectorBuilder__advance1__I__I__V($thiz, idx, xor) {
  if ((xor <= 0)) {
    throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), ((((((((((((((((("advance1(" + idx) + ", ") + xor) + "): a1=") + $thiz.af) + ", a2=") + $thiz.H) + ", a3=") + $thiz.N) + ", a4=") + $thiz.a1) + ", a5=") + $thiz.au) + ", a6=") + $thiz.bg) + ", depth=") + $thiz.P));
  } else if ((xor < 1024)) {
    if (($thiz.P <= 1)) {
      $thiz.H = new ($d_O.r().r().C)(32);
      $thiz.H.a[0] = $thiz.af;
      $thiz.P = 2;
    }
    $thiz.af = new $ac_O(32);
    $thiz.H.a[(31 & ((idx >>> 5) | 0))] = $thiz.af;
  } else if ((xor < 32768)) {
    if (($thiz.P <= 2)) {
      $thiz.N = new ($d_O.r().r().r().C)(32);
      $thiz.N.a[0] = $thiz.H;
      $thiz.P = 3;
    }
    $thiz.af = new $ac_O(32);
    $thiz.H = new ($d_O.r().r().C)(32);
    $thiz.H.a[(31 & ((idx >>> 5) | 0))] = $thiz.af;
    $thiz.N.a[(31 & ((idx >>> 10) | 0))] = $thiz.H;
  } else if ((xor < 1048576)) {
    if (($thiz.P <= 3)) {
      $thiz.a1 = new ($d_O.r().r().r().r().C)(32);
      $thiz.a1.a[0] = $thiz.N;
      $thiz.P = 4;
    }
    $thiz.af = new $ac_O(32);
    $thiz.H = new ($d_O.r().r().C)(32);
    $thiz.N = new ($d_O.r().r().r().C)(32);
    $thiz.H.a[(31 & ((idx >>> 5) | 0))] = $thiz.af;
    $thiz.N.a[(31 & ((idx >>> 10) | 0))] = $thiz.H;
    $thiz.a1.a[(31 & ((idx >>> 15) | 0))] = $thiz.N;
  } else if ((xor < 33554432)) {
    if (($thiz.P <= 4)) {
      $thiz.au = new ($d_O.r().r().r().r().r().C)(32);
      $thiz.au.a[0] = $thiz.a1;
      $thiz.P = 5;
    }
    $thiz.af = new $ac_O(32);
    $thiz.H = new ($d_O.r().r().C)(32);
    $thiz.N = new ($d_O.r().r().r().C)(32);
    $thiz.a1 = new ($d_O.r().r().r().r().C)(32);
    $thiz.H.a[(31 & ((idx >>> 5) | 0))] = $thiz.af;
    $thiz.N.a[(31 & ((idx >>> 10) | 0))] = $thiz.H;
    $thiz.a1.a[(31 & ((idx >>> 15) | 0))] = $thiz.N;
    $thiz.au.a[(31 & ((idx >>> 20) | 0))] = $thiz.a1;
  } else {
    if (($thiz.P <= 5)) {
      $thiz.bg = new ($d_O.r().r().r().r().r().r().C)(64);
      $thiz.bg.a[0] = $thiz.au;
      $thiz.P = 6;
    }
    $thiz.af = new $ac_O(32);
    $thiz.H = new ($d_O.r().r().C)(32);
    $thiz.N = new ($d_O.r().r().r().C)(32);
    $thiz.a1 = new ($d_O.r().r().r().r().C)(32);
    $thiz.au = new ($d_O.r().r().r().r().r().C)(32);
    $thiz.H.a[(31 & ((idx >>> 5) | 0))] = $thiz.af;
    $thiz.N.a[(31 & ((idx >>> 10) | 0))] = $thiz.H;
    $thiz.a1.a[(31 & ((idx >>> 15) | 0))] = $thiz.N;
    $thiz.au.a[(31 & ((idx >>> 20) | 0))] = $thiz.a1;
    $thiz.bg.a[((idx >>> 25) | 0)] = $thiz.au;
  }
}
/** @constructor */
function $c_sci_VectorBuilder() {
  this.bg = null;
  this.au = null;
  this.a1 = null;
  this.N = null;
  this.H = null;
  this.af = null;
  this.O = 0;
  this.C = 0;
  this.L = 0;
  this.iv = false;
  this.P = 0;
  this.af = new $ac_O(32);
  this.O = 0;
  this.C = 0;
  this.L = 0;
  this.iv = false;
  this.P = 1;
}
$p = $c_sci_VectorBuilder.prototype = new $h_O();
$p.constructor = $c_sci_VectorBuilder;
/** @constructor */
function $h_sci_VectorBuilder() {
}
$h_sci_VectorBuilder.prototype = $p;
$p.aZ = (function(size) {
});
$p.rc = (function(v) {
  var x28 = v.dY();
  switch (x28) {
    case 0: {
      break;
    }
    case 1: {
      this.P = 1;
      var i = v.e.a.length;
      this.O = (31 & i);
      this.C = ((i - this.O) | 0);
      var a = v.e;
      this.af = ((a.a.length === 32) ? a : $m_ju_Arrays$().aN(a, 0, 32));
      break;
    }
    case 3: {
      var d2 = v.bn;
      var a$1 = v.i;
      this.af = ((a$1.a.length === 32) ? a$1 : $m_ju_Arrays$().aN(a$1, 0, 32));
      this.P = 2;
      this.L = ((32 - v.bP) | 0);
      var i$1 = ((v.j + this.L) | 0);
      this.O = (31 & i$1);
      this.C = ((i$1 - this.O) | 0);
      this.H = new ($d_O.r().r().C)(32);
      this.H.a[0] = v.e;
      var dest = this.H;
      var length = d2.a.length;
      d2.s(0, dest, 1, length);
      this.H.a[((1 + d2.a.length) | 0)] = this.af;
      break;
    }
    case 5: {
      var d3 = v.aX;
      var s2 = v.b3;
      var a$2 = v.i;
      this.af = ((a$2.a.length === 32) ? a$2 : $m_ju_Arrays$().aN(a$2, 0, 32));
      this.P = 3;
      this.L = ((1024 - v.bf) | 0);
      var i$2 = ((v.j + this.L) | 0);
      this.O = (31 & i$2);
      this.C = ((i$2 - this.O) | 0);
      this.N = new ($d_O.r().r().r().C)(32);
      this.N.a[0] = $m_sci_VectorStatics$().B(v.e, v.bx);
      var dest$1 = this.N;
      var length$1 = d3.a.length;
      d3.s(0, dest$1, 1, length$1);
      this.H = $m_ju_Arrays$().a9(s2, 32);
      this.N.a[((1 + d3.a.length) | 0)] = this.H;
      this.H.a[s2.a.length] = this.af;
      break;
    }
    case 7: {
      var d4 = v.as;
      var s3 = v.aB;
      var s2$2 = v.aA;
      var a$3 = v.i;
      this.af = ((a$3.a.length === 32) ? a$3 : $m_ju_Arrays$().aN(a$3, 0, 32));
      this.P = 4;
      this.L = ((32768 - v.aQ) | 0);
      var i$3 = ((v.j + this.L) | 0);
      this.O = (31 & i$3);
      this.C = ((i$3 - this.O) | 0);
      this.a1 = new ($d_O.r().r().r().r().C)(32);
      this.a1.a[0] = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(v.e, v.b5), v.b6);
      var dest$2 = this.a1;
      var length$2 = d4.a.length;
      d4.s(0, dest$2, 1, length$2);
      this.N = $m_ju_Arrays$().a9(s3, 32);
      this.H = $m_ju_Arrays$().a9(s2$2, 32);
      this.a1.a[((1 + d4.a.length) | 0)] = this.N;
      this.N.a[s3.a.length] = this.H;
      this.H.a[s2$2.a.length] = this.af;
      break;
    }
    case 9: {
      var d5 = v.a0;
      var s4 = v.a8;
      var s3$2 = v.a7;
      var s2$3 = v.a6;
      var a$4 = v.i;
      this.af = ((a$4.a.length === 32) ? a$4 : $m_ju_Arrays$().aN(a$4, 0, 32));
      this.P = 5;
      this.L = ((1048576 - v.ai) | 0);
      var i$4 = ((v.j + this.L) | 0);
      this.O = (31 & i$4);
      this.C = ((i$4 - this.O) | 0);
      this.au = new ($d_O.r().r().r().r().r().C)(32);
      this.au.a[0] = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(v.e, v.aD), v.aE), v.aF);
      var dest$3 = this.au;
      var length$3 = d5.a.length;
      d5.s(0, dest$3, 1, length$3);
      this.a1 = $m_ju_Arrays$().a9(s4, 32);
      this.N = $m_ju_Arrays$().a9(s3$2, 32);
      this.H = $m_ju_Arrays$().a9(s2$3, 32);
      this.au.a[((1 + d5.a.length) | 0)] = this.a1;
      this.a1.a[s4.a.length] = this.N;
      this.N.a[s3$2.a.length] = this.H;
      this.H.a[s2$3.a.length] = this.af;
      break;
    }
    case 11: {
      var d6 = v.R;
      var s5 = v.Y;
      var s4$2 = v.X;
      var s3$3 = v.W;
      var s2$4 = v.V;
      var a$5 = v.i;
      this.af = ((a$5.a.length === 32) ? a$5 : $m_ju_Arrays$().aN(a$5, 0, 32));
      this.P = 6;
      this.L = ((33554432 - v.ae) | 0);
      var i$5 = ((v.j + this.L) | 0);
      this.O = (31 & i$5);
      this.C = ((i$5 - this.O) | 0);
      this.bg = new ($d_O.r().r().r().r().r().r().C)(64);
      this.bg.a[0] = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(v.e, v.ak), v.al), v.am), v.an);
      var dest$4 = this.bg;
      var length$4 = d6.a.length;
      d6.s(0, dest$4, 1, length$4);
      this.au = $m_ju_Arrays$().a9(s5, 32);
      this.a1 = $m_ju_Arrays$().a9(s4$2, 32);
      this.N = $m_ju_Arrays$().a9(s3$3, 32);
      this.H = $m_ju_Arrays$().a9(s2$4, 32);
      this.bg.a[((1 + d6.a.length) | 0)] = this.au;
      this.au.a[s5.a.length] = this.a1;
      this.a1.a[s4$2.a.length] = this.N;
      this.N.a[s3$3.a.length] = this.H;
      this.H.a[s2$4.a.length] = this.af;
      break;
    }
    default: {
      throw new $c_s_MatchError(x28);
    }
  }
  if (((this.O === 0) && (this.C > 0))) {
    this.O = 32;
    this.C = ((this.C - 32) | 0);
  }
  return this;
});
$p.pS = (function(elem) {
  if ((this.O === 32)) {
    $p_sci_VectorBuilder__advance__V(this);
  }
  this.af.a[this.O] = elem;
  this.O = ((1 + this.O) | 0);
  return this;
});
$p.nH = (function(xs) {
  return ((xs instanceof $c_sci_Vector) ? ((((this.O === 0) && (this.C === 0)) && (!this.iv)) ? this.rc(xs) : $p_sci_VectorBuilder__addVector__sci_Vector__sci_VectorBuilder(this, xs)) : $f_scm_Growable__addAll__sc_IterableOnce__scm_Growable(this, xs));
});
$p.oJ = (function() {
  if (this.iv) {
    $p_sci_VectorBuilder__leftAlignPrefix__V(this);
  }
  var len = ((this.O + this.C) | 0);
  var realLen = ((len - this.L) | 0);
  if ((realLen === 0)) {
    $m_sci_Vector$();
    return $m_sci_Vector0$();
  } else if ((len < 0)) {
    throw $ct_jl_IndexOutOfBoundsException__T__(new $c_jl_IndexOutOfBoundsException(), ("Vector cannot have negative size " + len));
  } else if ((len <= 32)) {
    var a = this.af;
    return new $c_sci_Vector1(((a.a.length === realLen) ? a : $m_ju_Arrays$().a9(a, realLen)));
  } else if ((len <= 1024)) {
    var i1 = (31 & ((len - 1) | 0));
    var i2 = ((((len - 1) | 0) >>> 5) | 0);
    var data = $m_ju_Arrays$().aN(this.H, 1, i2);
    var prefix1 = this.H.a[0];
    var a$1 = this.H.a[i2];
    var len$1 = ((1 + i1) | 0);
    var suffix1 = ((a$1.a.length === len$1) ? a$1 : $m_ju_Arrays$().a9(a$1, len$1));
    return new $c_sci_Vector2(prefix1, ((32 - this.L) | 0), data, suffix1, realLen);
  } else if ((len <= 32768)) {
    var i1$2 = (31 & ((len - 1) | 0));
    var i2$2 = (31 & ((((len - 1) | 0) >>> 5) | 0));
    var i3 = ((((len - 1) | 0) >>> 10) | 0);
    var data$2 = $m_ju_Arrays$().aN(this.N, 1, i3);
    var a$2 = this.N.a[0];
    var prefix2 = $m_ju_Arrays$().aN(a$2, 1, a$2.a.length);
    var prefix1$2 = this.N.a[0].a[0];
    var suffix2 = $m_ju_Arrays$().a9(this.N.a[i3], i2$2);
    var a$3 = this.N.a[i3].a[i2$2];
    var len$2 = ((1 + i1$2) | 0);
    var suffix1$2 = ((a$3.a.length === len$2) ? a$3 : $m_ju_Arrays$().a9(a$3, len$2));
    var len1 = prefix1$2.a.length;
    return new $c_sci_Vector3(prefix1$2, len1, prefix2, ((len1 + (prefix2.a.length << 5)) | 0), data$2, suffix2, suffix1$2, realLen);
  } else if ((len <= 1048576)) {
    var i1$3 = (31 & ((len - 1) | 0));
    var i2$3 = (31 & ((((len - 1) | 0) >>> 5) | 0));
    var i3$2 = (31 & ((((len - 1) | 0) >>> 10) | 0));
    var i4 = ((((len - 1) | 0) >>> 15) | 0);
    var data$3 = $m_ju_Arrays$().aN(this.a1, 1, i4);
    var a$4 = this.a1.a[0];
    var prefix3 = $m_ju_Arrays$().aN(a$4, 1, a$4.a.length);
    var a$5 = this.a1.a[0].a[0];
    var prefix2$2 = $m_ju_Arrays$().aN(a$5, 1, a$5.a.length);
    var prefix1$3 = this.a1.a[0].a[0].a[0];
    var suffix3 = $m_ju_Arrays$().a9(this.a1.a[i4], i3$2);
    var suffix2$2 = $m_ju_Arrays$().a9(this.a1.a[i4].a[i3$2], i2$3);
    var a$6 = this.a1.a[i4].a[i3$2].a[i2$3];
    var len$3 = ((1 + i1$3) | 0);
    var suffix1$3 = ((a$6.a.length === len$3) ? a$6 : $m_ju_Arrays$().a9(a$6, len$3));
    var len1$2 = prefix1$3.a.length;
    var len12$2 = ((len1$2 + (prefix2$2.a.length << 5)) | 0);
    return new $c_sci_Vector4(prefix1$3, len1$2, prefix2$2, len12$2, prefix3, ((len12$2 + (prefix3.a.length << 10)) | 0), data$3, suffix3, suffix2$2, suffix1$3, realLen);
  } else if ((len <= 33554432)) {
    var i1$4 = (31 & ((len - 1) | 0));
    var i2$4 = (31 & ((((len - 1) | 0) >>> 5) | 0));
    var i3$3 = (31 & ((((len - 1) | 0) >>> 10) | 0));
    var i4$2 = (31 & ((((len - 1) | 0) >>> 15) | 0));
    var i5 = ((((len - 1) | 0) >>> 20) | 0);
    var data$4 = $m_ju_Arrays$().aN(this.au, 1, i5);
    var a$7 = this.au.a[0];
    var prefix4 = $m_ju_Arrays$().aN(a$7, 1, a$7.a.length);
    var a$8 = this.au.a[0].a[0];
    var prefix3$2 = $m_ju_Arrays$().aN(a$8, 1, a$8.a.length);
    var a$9 = this.au.a[0].a[0].a[0];
    var prefix2$3 = $m_ju_Arrays$().aN(a$9, 1, a$9.a.length);
    var prefix1$4 = this.au.a[0].a[0].a[0].a[0];
    var suffix4 = $m_ju_Arrays$().a9(this.au.a[i5], i4$2);
    var suffix3$2 = $m_ju_Arrays$().a9(this.au.a[i5].a[i4$2], i3$3);
    var suffix2$3 = $m_ju_Arrays$().a9(this.au.a[i5].a[i4$2].a[i3$3], i2$4);
    var a$10 = this.au.a[i5].a[i4$2].a[i3$3].a[i2$4];
    var len$4 = ((1 + i1$4) | 0);
    var suffix1$4 = ((a$10.a.length === len$4) ? a$10 : $m_ju_Arrays$().a9(a$10, len$4));
    var len1$3 = prefix1$4.a.length;
    var len12$3 = ((len1$3 + (prefix2$3.a.length << 5)) | 0);
    var len123$2 = ((len12$3 + (prefix3$2.a.length << 10)) | 0);
    return new $c_sci_Vector5(prefix1$4, len1$3, prefix2$3, len12$3, prefix3$2, len123$2, prefix4, ((len123$2 + (prefix4.a.length << 15)) | 0), data$4, suffix4, suffix3$2, suffix2$3, suffix1$4, realLen);
  } else {
    var i1$5 = (31 & ((len - 1) | 0));
    var i2$5 = (31 & ((((len - 1) | 0) >>> 5) | 0));
    var i3$4 = (31 & ((((len - 1) | 0) >>> 10) | 0));
    var i4$3 = (31 & ((((len - 1) | 0) >>> 15) | 0));
    var i5$2 = (31 & ((((len - 1) | 0) >>> 20) | 0));
    var i6 = ((((len - 1) | 0) >>> 25) | 0);
    var data$5 = $m_ju_Arrays$().aN(this.bg, 1, i6);
    var a$11 = this.bg.a[0];
    var prefix5 = $m_ju_Arrays$().aN(a$11, 1, a$11.a.length);
    var a$12 = this.bg.a[0].a[0];
    var prefix4$2 = $m_ju_Arrays$().aN(a$12, 1, a$12.a.length);
    var a$13 = this.bg.a[0].a[0].a[0];
    var prefix3$3 = $m_ju_Arrays$().aN(a$13, 1, a$13.a.length);
    var a$14 = this.bg.a[0].a[0].a[0].a[0];
    var prefix2$4 = $m_ju_Arrays$().aN(a$14, 1, a$14.a.length);
    var prefix1$5 = this.bg.a[0].a[0].a[0].a[0].a[0];
    var suffix5 = $m_ju_Arrays$().a9(this.bg.a[i6], i5$2);
    var suffix4$2 = $m_ju_Arrays$().a9(this.bg.a[i6].a[i5$2], i4$3);
    var suffix3$3 = $m_ju_Arrays$().a9(this.bg.a[i6].a[i5$2].a[i4$3], i3$4);
    var suffix2$4 = $m_ju_Arrays$().a9(this.bg.a[i6].a[i5$2].a[i4$3].a[i3$4], i2$5);
    var a$15 = this.bg.a[i6].a[i5$2].a[i4$3].a[i3$4].a[i2$5];
    var len$5 = ((1 + i1$5) | 0);
    var suffix1$5 = ((a$15.a.length === len$5) ? a$15 : $m_ju_Arrays$().a9(a$15, len$5));
    var len1$4 = prefix1$5.a.length;
    var len12$4 = ((len1$4 + (prefix2$4.a.length << 5)) | 0);
    var len123$3 = ((len12$4 + (prefix3$3.a.length << 10)) | 0);
    var len1234$2 = ((len123$3 + (prefix4$2.a.length << 15)) | 0);
    return new $c_sci_Vector6(prefix1$5, len1$4, prefix2$4, len12$4, prefix3$3, len123$3, prefix4$2, len1234$2, prefix5, ((len1234$2 + (prefix5.a.length << 20)) | 0), data$5, suffix5, suffix4$2, suffix3$3, suffix2$4, suffix1$5, realLen);
  }
});
$p.t = (function() {
  return (((((((("VectorBuilder(len1=" + this.O) + ", lenRest=") + this.C) + ", offset=") + this.L) + ", depth=") + this.P) + ")");
});
$p.aq = (function(elem) {
  return this.pS(elem);
});
$p.b7 = (function(elems) {
  return this.nH(elems);
});
$p.aU = (function() {
  return this.oJ();
});
var $d_sci_VectorBuilder = new $TypeData().i($c_sci_VectorBuilder, "scala.collection.immutable.VectorBuilder", ({
  gD: 1,
  H: 1,
  I: 1,
  K: 1,
  a7: 1
}));
/** @constructor */
function $c_scm_ArrayBuffer$() {
  this.n4 = null;
  $n_scm_ArrayBuffer$ = this;
  this.n4 = new $ac_O(0);
}
$p = $c_scm_ArrayBuffer$.prototype = new $h_O();
$p.constructor = $c_scm_ArrayBuffer$;
/** @constructor */
function $h_scm_ArrayBuffer$() {
}
$h_scm_ArrayBuffer$.prototype = $p;
$p.em = (function(elems) {
  return this.ok(elems);
});
$p.ok = (function(coll) {
  var k = coll.q();
  if ((k >= 0)) {
    var array = this.oP(this.n4, 0, k);
    var actual = ($is_sc_Iterable(coll) ? coll.bc(array, 0, 2147483647) : coll.c().bc(array, 0, 2147483647));
    if ((actual !== k)) {
      throw $ct_jl_IllegalStateException__T__(new $c_jl_IllegalStateException(), ((("Copied " + actual) + " of ") + k));
    }
    return $ct_scm_ArrayBuffer__AO__I__(new $c_scm_ArrayBuffer(), array, k);
  } else {
    return $ct_scm_ArrayBuffer__(new $c_scm_ArrayBuffer()).nI(coll);
  }
});
$p.T = (function() {
  return new $c_scm_ArrayBuffer$$anon$1();
});
$p.rP = (function(arrayLen, targetLen) {
  if ((targetLen < 0)) {
    throw $ct_jl_RuntimeException__T__(new $c_jl_RuntimeException(), ((((("Overflow while resizing array of array-backed collection. Requested length: " + targetLen) + "; current length: ") + arrayLen) + "; increase: ") + ((targetLen - arrayLen) | 0)));
  } else if ((targetLen <= arrayLen)) {
    return (-1);
  } else {
    if ((targetLen > 2147483639)) {
      throw $ct_jl_RuntimeException__T__(new $c_jl_RuntimeException(), ((("Array of array-backed collection exceeds VM length limit of 2147483639. Requested length: " + targetLen) + "; current length: ") + arrayLen));
    }
    if ((arrayLen > 1073741819)) {
      return 2147483639;
    } else {
      var x = (arrayLen << 1);
      var y = ((x > 16) ? x : 16);
      return ((targetLen > y) ? targetLen : y);
    }
  }
});
$p.oP = (function(array, curSize, targetSize) {
  var newLen = this.rP(array.a.length, targetSize);
  if ((newLen < 0)) {
    return array;
  } else {
    var res = new $ac_O(newLen);
    array.s(0, res, 0, curSize);
    return res;
  }
});
$p.a3 = (function(source) {
  return this.ok(source);
});
var $d_scm_ArrayBuffer$ = new $TypeData().i($c_scm_ArrayBuffer$, "scala.collection.mutable.ArrayBuffer$", ({
  gJ: 1,
  a: 1,
  B: 1,
  W: 1,
  at: 1
}));
var $n_scm_ArrayBuffer$;
function $m_scm_ArrayBuffer$() {
  if ((!$n_scm_ArrayBuffer$)) {
    $n_scm_ArrayBuffer$ = new $c_scm_ArrayBuffer$();
  }
  return $n_scm_ArrayBuffer$;
}
/** @constructor */
function $c_scm_ArrayBuffer$$anon$1() {
  this.dJ = null;
  $ct_scm_GrowableBuilder__scm_Growable__(this, ($m_scm_ArrayBuffer$(), $ct_scm_ArrayBuffer__(new $c_scm_ArrayBuffer())));
}
$p = $c_scm_ArrayBuffer$$anon$1.prototype = new $h_scm_GrowableBuilder();
$p.constructor = $c_scm_ArrayBuffer$$anon$1;
/** @constructor */
function $h_scm_ArrayBuffer$$anon$1() {
}
$h_scm_ArrayBuffer$$anon$1.prototype = $p;
$p.aZ = (function(size) {
  this.dJ.aZ(size);
});
var $d_scm_ArrayBuffer$$anon$1 = new $TypeData().i($c_scm_ArrayBuffer$$anon$1, "scala.collection.mutable.ArrayBuffer$$anon$1", ({
  gK: 1,
  aI: 1,
  H: 1,
  I: 1,
  K: 1
}));
/** @constructor */
function $c_scm_Buffer$() {
  this.e5 = null;
  $ct_sc_SeqFactory$Delegate__sc_SeqFactory__(this, $m_sjs_js_WrappedArray$());
}
$p = $c_scm_Buffer$.prototype = new $h_sc_SeqFactory$Delegate();
$p.constructor = $c_scm_Buffer$;
/** @constructor */
function $h_scm_Buffer$() {
}
$h_scm_Buffer$.prototype = $p;
var $d_scm_Buffer$ = new $TypeData().i($c_scm_Buffer$, "scala.collection.mutable.Buffer$", ({
  gQ: 1,
  aD: 1,
  a: 1,
  B: 1,
  W: 1
}));
var $n_scm_Buffer$;
function $m_scm_Buffer$() {
  if ((!$n_scm_Buffer$)) {
    $n_scm_Buffer$ = new $c_scm_Buffer$();
  }
  return $n_scm_Buffer$;
}
/** @constructor */
function $c_scm_HashMap$$anon$6(initialCapacity$1, loadFactor$1) {
  this.dJ = null;
  $ct_scm_GrowableBuilder__scm_Growable__(this, $ct_scm_HashMap__I__D__(new $c_scm_HashMap(), initialCapacity$1, loadFactor$1));
}
$p = $c_scm_HashMap$$anon$6.prototype = new $h_scm_GrowableBuilder();
$p.constructor = $c_scm_HashMap$$anon$6;
/** @constructor */
function $h_scm_HashMap$$anon$6() {
}
$h_scm_HashMap$$anon$6.prototype = $p;
$p.aZ = (function(size) {
  this.dJ.aZ(size);
});
var $d_scm_HashMap$$anon$6 = new $TypeData().i($c_scm_HashMap$$anon$6, "scala.collection.mutable.HashMap$$anon$6", ({
  h0: 1,
  aI: 1,
  H: 1,
  I: 1,
  K: 1
}));
function $ct_scm_HashMap$HashMapIterator__scm_HashMap__($thiz, outer) {
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $thiz.hc = outer;
  $thiz.eS = 0;
  $thiz.ef = null;
  $thiz.hd = outer.Q.a.length;
  return $thiz;
}
/** @constructor */
function $c_scm_HashMap$HashMapIterator() {
  this.eS = 0;
  this.ef = null;
  this.hd = 0;
  this.hc = null;
}
$p = $c_scm_HashMap$HashMapIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_scm_HashMap$HashMapIterator;
/** @constructor */
function $h_scm_HashMap$HashMapIterator() {
}
$h_scm_HashMap$HashMapIterator.prototype = $p;
$p.k = (function() {
  if ((this.ef !== null)) {
    return true;
  } else {
    while ((this.eS < this.hd)) {
      var n = this.hc.Q.a[this.eS];
      this.eS = ((1 + this.eS) | 0);
      if ((n !== null)) {
        this.ef = n;
        return true;
      }
    }
    return false;
  }
});
$p.f = (function() {
  if ((!this.k())) {
    return $m_sc_Iterator$().G.f();
  } else {
    var x$proxy14 = this.ef;
    if ((x$proxy14 === null)) {
      $m_sr_Scala3RunTime$().bV();
    }
    var r = this.kC(x$proxy14);
    var x$proxy15 = this.ef;
    if ((x$proxy15 === null)) {
      $m_sr_Scala3RunTime$().bV();
    }
    this.ef = x$proxy15.bh;
    return r;
  }
});
/** @constructor */
function $c_scm_HashSet$$anon$4(initialCapacity$1, loadFactor$1) {
  this.dJ = null;
  $ct_scm_GrowableBuilder__scm_Growable__(this, $ct_scm_HashSet__I__D__(new $c_scm_HashSet(), initialCapacity$1, loadFactor$1));
}
$p = $c_scm_HashSet$$anon$4.prototype = new $h_scm_GrowableBuilder();
$p.constructor = $c_scm_HashSet$$anon$4;
/** @constructor */
function $h_scm_HashSet$$anon$4() {
}
$h_scm_HashSet$$anon$4.prototype = $p;
$p.aZ = (function(size) {
  this.dJ.aZ(size);
});
var $d_scm_HashSet$$anon$4 = new $TypeData().i($c_scm_HashSet$$anon$4, "scala.collection.mutable.HashSet$$anon$4", ({
  h6: 1,
  aI: 1,
  H: 1,
  I: 1,
  K: 1
}));
function $ct_scm_HashSet$HashSetIterator__scm_HashSet__($thiz, outer) {
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $thiz.he = outer;
  $thiz.eV = 0;
  $thiz.eg = null;
  $thiz.hf = outer.bA.a.length;
  return $thiz;
}
/** @constructor */
function $c_scm_HashSet$HashSetIterator() {
  this.eV = 0;
  this.eg = null;
  this.hf = 0;
  this.he = null;
}
$p = $c_scm_HashSet$HashSetIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_scm_HashSet$HashSetIterator;
/** @constructor */
function $h_scm_HashSet$HashSetIterator() {
}
$h_scm_HashSet$HashSetIterator.prototype = $p;
$p.k = (function() {
  if ((this.eg !== null)) {
    return true;
  } else {
    while ((this.eV < this.hf)) {
      var n = this.he.bA.a[this.eV];
      this.eV = ((1 + this.eV) | 0);
      if ((n !== null)) {
        this.eg = n;
        return true;
      }
    }
    return false;
  }
});
$p.f = (function() {
  if ((!this.k())) {
    return $m_sc_Iterator$().G.f();
  } else {
    var x$proxy10 = this.eg;
    if ((x$proxy10 === null)) {
      $m_sr_Scala3RunTime$().bV();
    }
    var r = this.kD(x$proxy10);
    var x$proxy11 = this.eg;
    if ((x$proxy11 === null)) {
      $m_sr_Scala3RunTime$().bV();
    }
    this.eg = x$proxy11.bB;
    return r;
  }
});
function $ct_scm_ImmutableBuilder__sc_IterableOnce__($thiz, empty) {
  $thiz.hg = empty;
  return $thiz;
}
/** @constructor */
function $c_scm_ImmutableBuilder() {
  this.hg = null;
}
$p = $c_scm_ImmutableBuilder.prototype = new $h_O();
$p.constructor = $c_scm_ImmutableBuilder;
/** @constructor */
function $h_scm_ImmutableBuilder() {
}
$h_scm_ImmutableBuilder.prototype = $p;
$p.b7 = (function(elems) {
  return $f_scm_Growable__addAll__sc_IterableOnce__scm_Growable(this, elems);
});
$p.aZ = (function(size) {
});
$p.aU = (function() {
  return this.hg;
});
/** @constructor */
function $c_scm_IndexedSeq$() {
  this.e5 = null;
  $ct_sc_SeqFactory$Delegate__sc_SeqFactory__(this, $m_scm_ArrayBuffer$());
}
$p = $c_scm_IndexedSeq$.prototype = new $h_sc_SeqFactory$Delegate();
$p.constructor = $c_scm_IndexedSeq$;
/** @constructor */
function $h_scm_IndexedSeq$() {
}
$h_scm_IndexedSeq$.prototype = $p;
var $d_scm_IndexedSeq$ = new $TypeData().i($c_scm_IndexedSeq$, "scala.collection.mutable.IndexedSeq$", ({
  h9: 1,
  aD: 1,
  a: 1,
  B: 1,
  W: 1
}));
var $n_scm_IndexedSeq$;
function $m_scm_IndexedSeq$() {
  if ((!$n_scm_IndexedSeq$)) {
    $n_scm_IndexedSeq$ = new $c_scm_IndexedSeq$();
  }
  return $n_scm_IndexedSeq$;
}
/** @constructor */
function $c_scm_ListBuffer$() {
}
$p = $c_scm_ListBuffer$.prototype = new $h_O();
$p.constructor = $c_scm_ListBuffer$;
/** @constructor */
function $h_scm_ListBuffer$() {
}
$h_scm_ListBuffer$.prototype = $p;
$p.em = (function(elems) {
  return new $c_scm_ListBuffer().j8(elems);
});
$p.T = (function() {
  return $ct_scm_GrowableBuilder__scm_Growable__(new $c_scm_GrowableBuilder(), new $c_scm_ListBuffer());
});
$p.a3 = (function(source) {
  return new $c_scm_ListBuffer().j8(source);
});
var $d_scm_ListBuffer$ = new $TypeData().i($c_scm_ListBuffer$, "scala.collection.mutable.ListBuffer$", ({
  hd: 1,
  a: 1,
  B: 1,
  W: 1,
  at: 1
}));
var $n_scm_ListBuffer$;
function $m_scm_ListBuffer$() {
  if ((!$n_scm_ListBuffer$)) {
    $n_scm_ListBuffer$ = new $c_scm_ListBuffer$();
  }
  return $n_scm_ListBuffer$;
}
/** @constructor */
function $c_scm_MutationTracker$CheckedIterator(underlying, mutationCount) {
  this.k6 = null;
  this.nf = null;
  this.ne = 0;
  this.k6 = underlying;
  this.nf = mutationCount;
  this.ne = (mutationCount.a2() | 0);
}
$p = $c_scm_MutationTracker$CheckedIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_scm_MutationTracker$CheckedIterator;
/** @constructor */
function $h_scm_MutationTracker$CheckedIterator() {
}
$h_scm_MutationTracker$CheckedIterator.prototype = $p;
$p.k = (function() {
  $m_scm_MutationTracker$().kp(this.ne, (this.nf.a2() | 0), "mutation occurred during iteration");
  return this.k6.k();
});
$p.f = (function() {
  return this.k6.f();
});
var $d_scm_MutationTracker$CheckedIterator = new $TypeData().i($c_scm_MutationTracker$CheckedIterator, "scala.collection.mutable.MutationTracker$CheckedIterator", ({
  hh: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_s_concurrent_ExecutionContext$parasitic$() {
  this.nj = null;
  $n_s_concurrent_ExecutionContext$parasitic$ = this;
  this.nj = new $c_jl_ThreadLocal();
}
$p = $c_s_concurrent_ExecutionContext$parasitic$.prototype = new $h_O();
$p.constructor = $c_s_concurrent_ExecutionContext$parasitic$;
/** @constructor */
function $h_s_concurrent_ExecutionContext$parasitic$() {
}
$h_s_concurrent_ExecutionContext$parasitic$.prototype = $p;
$p.kB = (function(runnable) {
  $f_s_concurrent_BatchingExecutor__submitSyncBatched__jl_Runnable__V(this, runnable);
});
$p.l2 = (function(t) {
  $m_s_concurrent_ExecutionContext$().gl.d(t);
});
var $d_s_concurrent_ExecutionContext$parasitic$ = new $TypeData().i($c_s_concurrent_ExecutionContext$parasitic$, "scala.concurrent.ExecutionContext$parasitic$", ({
  hp: 1,
  b8: 1,
  aT: 1,
  b9: 1,
  hl: 1
}));
var $n_s_concurrent_ExecutionContext$parasitic$;
function $m_s_concurrent_ExecutionContext$parasitic$() {
  if ((!$n_s_concurrent_ExecutionContext$parasitic$)) {
    $n_s_concurrent_ExecutionContext$parasitic$ = new $c_s_concurrent_ExecutionContext$parasitic$();
  }
  return $n_s_concurrent_ExecutionContext$parasitic$;
}
/** @constructor */
function $c_s_concurrent_duration_Duration$Infinite() {
}
$p = $c_s_concurrent_duration_Duration$Infinite.prototype = new $h_s_concurrent_duration_Duration();
$p.constructor = $c_s_concurrent_duration_Duration$Infinite;
/** @constructor */
function $h_s_concurrent_duration_Duration$Infinite() {
}
$h_s_concurrent_duration_Duration$Infinite.prototype = $p;
function $p_s_concurrent_duration_FiniteDuration__bounded__J__Z($thiz, max_$_lo, max_$_hi) {
  var lo = ((-max_$_lo) | 0);
  var hi = ((((-max_$_hi) | 0) - ((lo !== 0) | 0)) | 0);
  var x_$_lo = $thiz.dj;
  var x_$_hi = $thiz.dk;
  if (((hi === x_$_hi) ? ((lo >>> 0) <= (x_$_lo >>> 0)) : (hi < x_$_hi))) {
    var x$1_$_lo = $thiz.dj;
    var x$1_$_hi = $thiz.dk;
    return ((x$1_$_hi === max_$_hi) ? ((x$1_$_lo >>> 0) <= (max_$_lo >>> 0)) : (x$1_$_hi < max_$_hi));
  } else {
    return false;
  }
}
function $p_s_concurrent_duration_FiniteDuration__unitString__T($thiz) {
  var $x_1 = $m_s_concurrent_duration_Duration$().np.d($thiz.eX);
  var x_$_lo = $thiz.dj;
  var x_$_hi = $thiz.dk;
  return ($x_1 + ((((1 ^ x_$_lo) | x_$_hi) === 0) ? "" : "s"));
}
/** @constructor */
function $c_s_concurrent_duration_FiniteDuration(length_$_lo, length_$_hi, unit) {
  this.dj = 0;
  this.dk = 0;
  this.eX = null;
  this.dj = length_$_lo;
  this.dk = length_$_hi;
  this.eX = unit;
  matchResult9: {
    var $x_1;
    var x = $m_s_concurrent_duration_package$().kb;
    if (((x === null) ? (unit === null) : (x === unit))) {
      var $x_1 = $p_s_concurrent_duration_FiniteDuration__bounded__J__Z(this, (-1), 2147483647);
      break matchResult9;
    }
    var x$3 = $m_s_concurrent_duration_package$().k9;
    if (((x$3 === null) ? (unit === null) : (x$3 === unit))) {
      var $x_1 = $p_s_concurrent_duration_FiniteDuration__bounded__J__Z(this, (-1511828489), 2147483);
      break matchResult9;
    }
    var x$5 = $m_s_concurrent_duration_package$().iB;
    if (((x$5 === null) ? (unit === null) : (x$5 === unit))) {
      var $x_1 = $p_s_concurrent_duration_FiniteDuration__bounded__J__Z(this, 2077252342, 2147);
      break matchResult9;
    }
    var x$7 = $m_s_concurrent_duration_package$().kc;
    if (((x$7 === null) ? (unit === null) : (x$7 === unit))) {
      var $x_1 = $p_s_concurrent_duration_FiniteDuration__bounded__J__Z(this, 633437444, 2);
      break matchResult9;
    }
    var x$9 = $m_s_concurrent_duration_package$().ka;
    if (((x$9 === null) ? (unit === null) : (x$9 === unit))) {
      var $x_1 = $p_s_concurrent_duration_FiniteDuration__bounded__J__Z(this, 153722867, 0);
      break matchResult9;
    }
    var x$11 = $m_s_concurrent_duration_package$().k8;
    if (((x$11 === null) ? (unit === null) : (x$11 === unit))) {
      var $x_1 = $p_s_concurrent_duration_FiniteDuration__bounded__J__Z(this, 2562047, 0);
      break matchResult9;
    }
    var x$13 = $m_s_concurrent_duration_package$().iA;
    if (((x$13 === null) ? (unit === null) : (x$13 === unit))) {
      var $x_1 = $p_s_concurrent_duration_FiniteDuration__bounded__J__Z(this, 106751, 0);
      break matchResult9;
    }
    throw new $c_s_MatchError(unit);
  }
  if ((!$x_1)) {
    throw $ct_jl_IllegalArgumentException__T__(new $c_jl_IllegalArgumentException(), "requirement failed: Duration is limited to +-(2^63-1)ns (ca. 292 years)");
  }
}
$p = $c_s_concurrent_duration_FiniteDuration.prototype = new $h_s_concurrent_duration_Duration();
$p.constructor = $c_s_concurrent_duration_FiniteDuration;
/** @constructor */
function $h_s_concurrent_duration_FiniteDuration() {
}
$h_s_concurrent_duration_FiniteDuration.prototype = $p;
$p.t = (function() {
  return (($s_RTLong__toString__I__I__T(this.dj, this.dk) + " ") + $p_s_concurrent_duration_FiniteDuration__unitString__T(this));
});
$p.kr = (function(other) {
  if ((other instanceof $c_s_concurrent_duration_FiniteDuration)) {
    var $x_1 = this.eX.du(this.dj, this.dk);
    var this$2 = new $c_sr_RichLong($x_1.l, $x_1.h);
    var $x_4 = other.eX;
    var $x_3 = other;
    var $x_2 = $x_4.du($x_3.dj, $x_3.dk);
    var y_$_lo = $x_2.l;
    var y_$_hi = $x_2.h;
    var x$1_$_lo = this$2.fy;
    var x$1_$_hi = this$2.fz;
    return ((x$1_$_hi === y_$_hi) ? ((x$1_$_lo === y_$_lo) ? 0 : (((x$1_$_lo >>> 0) < (y_$_lo >>> 0)) ? (-1) : 1)) : ((x$1_$_hi < y_$_hi) ? (-1) : 1));
  } else {
    return ((-other.kr(this)) | 0);
  }
});
$p.p = (function(other) {
  if ((other instanceof $c_s_concurrent_duration_FiniteDuration)) {
    var $x_1 = this.eX.du(this.dj, this.dk);
    var x_$_lo = $x_1.l;
    var x_$_hi = $x_1.h;
    var $x_4 = other.eX;
    var $x_3 = other;
    var $x_2 = $x_4.du($x_3.dj, $x_3.dk);
    var x$1_$_lo = $x_2.l;
    var x$1_$_hi = $x_2.h;
    return (((x_$_lo ^ x$1_$_lo) | (x_$_hi ^ x$1_$_hi)) === 0);
  } else {
    return (this === other);
  }
});
$p.u = (function() {
  var $x_1 = this.eX.du(this.dj, this.dk);
  return $s_RTLong__toInt__I__I__I($x_1.l, $x_1.h);
});
function $isArrayOf_s_concurrent_duration_FiniteDuration(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cO)));
}
var $d_s_concurrent_duration_FiniteDuration = new $TypeData().i($c_s_concurrent_duration_FiniteDuration, "scala.concurrent.duration.FiniteDuration", ({
  cO: 1,
  cN: 1,
  a: 1,
  F: 1,
  bb: 1
}));
function $f_s_math_Ordering__gt__O__O__Z($thiz, x, y) {
  return ($thiz.ax(x, y) > 0);
}
function $f_s_math_Ordering__isReverseOf__s_math_Ordering__Z($thiz, other) {
  if ((other instanceof $c_s_math_Ordering$Reverse)) {
    var x = other.eY;
    return ((x !== null) && x.p($thiz));
  } else {
    return false;
  }
}
function $f_s_reflect_ClassTag__equals__O__Z($thiz, x) {
  if ($is_s_reflect_ClassTag(x)) {
    var x$2 = $thiz.bk();
    var x$3 = x.bk();
    return ((x$2 === null) ? (x$3 === null) : (x$2 === x$3));
  } else {
    return false;
  }
}
function $p_s_reflect_ClassTag__prettyprint$1__jl_Class__T($thiz, clazz) {
  return (clazz.J.Z ? (("Array[" + $p_s_reflect_ClassTag__prettyprint$1__jl_Class__T($thiz, clazz.J.Q())) + "]") : clazz.J.N);
}
function $is_s_reflect_ClassTag(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.O)));
}
function $isArrayOf_s_reflect_ClassTag(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.O)));
}
/** @constructor */
function $c_sr_ScalaRunTime$$anon$1(x$1) {
  this.nx = null;
  this.hp = 0;
  this.nw = 0;
  this.nx = x$1;
  this.hp = 0;
  this.nw = x$1.bE();
}
$p = $c_sr_ScalaRunTime$$anon$1.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sr_ScalaRunTime$$anon$1;
/** @constructor */
function $h_sr_ScalaRunTime$$anon$1() {
}
$h_sr_ScalaRunTime$$anon$1.prototype = $p;
$p.k = (function() {
  return (this.hp < this.nw);
});
$p.f = (function() {
  var result = this.nx.bF(this.hp);
  this.hp = ((1 + this.hp) | 0);
  return result;
});
var $d_sr_ScalaRunTime$$anon$1 = new $TypeData().i($c_sr_ScalaRunTime$$anon$1, "scala.runtime.ScalaRunTime$$anon$1", ({
  iJ: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sjs_js_ArrayOps$ArrayIterator(xs) {
  this.hq = null;
  this.eZ = 0;
  this.hq = xs;
  this.eZ = 0;
}
$p = $c_sjs_js_ArrayOps$ArrayIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sjs_js_ArrayOps$ArrayIterator;
/** @constructor */
function $h_sjs_js_ArrayOps$ArrayIterator() {
}
$h_sjs_js_ArrayOps$ArrayIterator.prototype = $p;
$p.k = (function() {
  return (this.eZ < (this.hq.length | 0));
});
$p.f = (function() {
  if ((this.eZ >= (this.hq.length | 0))) {
    throw $ct_ju_NoSuchElementException__(new $c_ju_NoSuchElementException());
  }
  var r = this.hq[this.eZ];
  this.eZ = ((1 + this.eZ) | 0);
  return r;
});
$p.cE = (function(n) {
  if ((n > 0)) {
    this.eZ = (Math.min((this.hq.length | 0), ((this.eZ + n) | 0)) | 0);
  }
  return this;
});
var $d_sjs_js_ArrayOps$ArrayIterator = new $TypeData().i($c_sjs_js_ArrayOps$ArrayIterator, "scala.scalajs.js.ArrayOps$ArrayIterator", ({
  iT: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sjs_js_WrappedArray$() {
}
$p = $c_sjs_js_WrappedArray$.prototype = new $h_O();
$p.constructor = $c_sjs_js_WrappedArray$;
/** @constructor */
function $h_sjs_js_WrappedArray$() {
}
$h_sjs_js_WrappedArray$.prototype = $p;
$p.em = (function(elems) {
  return this.ol(elems);
});
$p.T = (function() {
  return $ct_sjs_js_WrappedArray__(new $c_sjs_js_WrappedArray());
});
$p.ol = (function(source) {
  return $f_scm_Growable__addAll__sc_IterableOnce__scm_Growable($ct_sjs_js_WrappedArray__(new $c_sjs_js_WrappedArray()), source).aU();
});
$p.a3 = (function(source) {
  return this.ol(source);
});
var $d_sjs_js_WrappedArray$ = new $TypeData().i($c_sjs_js_WrappedArray$, "scala.scalajs.js.WrappedArray$", ({
  iZ: 1,
  at: 1,
  a: 1,
  B: 1,
  W: 1
}));
var $n_sjs_js_WrappedArray$;
function $m_sjs_js_WrappedArray$() {
  if ((!$n_sjs_js_WrappedArray$)) {
    $n_sjs_js_WrappedArray$ = new $c_sjs_js_WrappedArray$();
  }
  return $n_sjs_js_WrappedArray$;
}
/** @constructor */
function $c_sjsr_WrappedVarArgs$() {
}
$p = $c_sjsr_WrappedVarArgs$.prototype = new $h_O();
$p.constructor = $c_sjsr_WrappedVarArgs$;
/** @constructor */
function $h_sjsr_WrappedVarArgs$() {
}
$h_sjsr_WrappedVarArgs$.prototype = $p;
$p.em = (function(elems) {
  return this.kK(elems);
});
$p.kK = (function(source) {
  return this.T().b7(source).aU();
});
$p.T = (function() {
  return new $c_scm_Builder$$anon$1(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$2$2) => new $c_sjsr_WrappedVarArgs(x$1$2$2.ek))), $ct_sjs_js_WrappedArray__sjs_js_Array__(new $c_sjs_js_WrappedArray(), []));
});
$p.a3 = (function(source) {
  return this.kK(source);
});
var $d_sjsr_WrappedVarArgs$ = new $TypeData().i($c_sjsr_WrappedVarArgs$, "scala.scalajs.runtime.WrappedVarArgs$", ({
  j5: 1,
  at: 1,
  a: 1,
  B: 1,
  W: 1
}));
var $n_sjsr_WrappedVarArgs$;
function $m_sjsr_WrappedVarArgs$() {
  if ((!$n_sjsr_WrappedVarArgs$)) {
    $n_sjsr_WrappedVarArgs$ = new $c_sjsr_WrappedVarArgs$();
  }
  return $n_sjsr_WrappedVarArgs$;
}
/** @constructor */
function $c_s_util_Failure(exception) {
  this.f0 = null;
  this.f0 = exception;
}
$p = $c_s_util_Failure.prototype = new $h_s_util_Try();
$p.constructor = $c_s_util_Failure;
/** @constructor */
function $h_s_util_Failure() {
}
$h_s_util_Failure.prototype = $p;
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().dr(this, (-1408943127), true);
});
$p.p = (function(x$0) {
  if ((this === x$0)) {
    return true;
  } else if ((x$0 instanceof $c_s_util_Failure)) {
    var x = this.f0;
    var x$2 = x$0.f0;
    return ((x === null) ? (x$2 === null) : x.p(x$2));
  } else {
    return false;
  }
});
$p.t = (function() {
  return $m_sr_ScalaRunTime$().dL(this);
});
$p.bE = (function() {
  return 1;
});
$p.bG = (function() {
  return "Failure";
});
$p.bF = (function(n) {
  if ((n === 0)) {
    return this.f0;
  }
  throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
});
$p.S = (function() {
  var $x_1 = this.f0;
  throw (($x_1 instanceof $c_sjs_js_JavaScriptException) ? $x_1.di : $x_1);
});
$p.dO = (function(f) {
});
$p.oF = (function(pf) {
  var marker = $m_sr_Statics$PFMarker$();
  try {
    var v = pf.bQ(this.f0, new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$2) => marker)));
    return ((marker !== v) ? new $c_s_util_Success(v) : this);
  } catch (e) {
    var e$2 = ((e instanceof $c_jl_Throwable) ? e : new $c_sjs_js_JavaScriptException(e));
    var x18 = $m_s_util_control_NonFatal$().sn(e$2);
    if ((!x18.g())) {
      return new $c_s_util_Failure(x18.S());
    }
    throw ((e$2 instanceof $c_sjs_js_JavaScriptException) ? e$2.di : e$2);
  }
});
function $isArrayOf_s_util_Failure(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.d0)));
}
var $d_s_util_Failure = new $TypeData().i($c_s_util_Failure, "scala.util.Failure", ({
  d0: 1,
  bd: 1,
  d: 1,
  J: 1,
  a: 1
}));
/** @constructor */
function $c_s_util_Success(value) {
  this.fx = null;
  this.fx = value;
}
$p = $c_s_util_Success.prototype = new $h_s_util_Try();
$p.constructor = $c_s_util_Success;
/** @constructor */
function $h_s_util_Success() {
}
$h_s_util_Success.prototype = $p;
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().dr(this, (-1750213842), true);
});
$p.p = (function(x$0) {
  return ((this === x$0) || ((x$0 instanceof $c_s_util_Success) && $m_sr_BoxesRunTime$().n(this.fx, x$0.fx)));
});
$p.t = (function() {
  return $m_sr_ScalaRunTime$().dL(this);
});
$p.bE = (function() {
  return 1;
});
$p.bG = (function() {
  return "Success";
});
$p.bF = (function(n) {
  if ((n === 0)) {
    return this.fx;
  }
  throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
});
$p.S = (function() {
  return this.fx;
});
$p.dO = (function(f) {
  f.d(this.fx);
});
$p.oF = (function(pf) {
  return this;
});
function $isArrayOf_s_util_Success(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.d1)));
}
var $d_s_util_Success = new $TypeData().i($c_s_util_Success, "scala.util.Success", ({
  d1: 1,
  bd: 1,
  d: 1,
  J: 1,
  a: 1
}));
function $ct_jl_ArrayIndexOutOfBoundsException__T__($thiz, s) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, s, null, true, true);
  return $thiz;
}
function $ct_jl_ArrayIndexOutOfBoundsException__($thiz) {
  $ct_jl_Throwable__T__jl_Throwable__Z__Z__($thiz, null, null, true, true);
  return $thiz;
}
class $c_jl_ArrayIndexOutOfBoundsException extends $c_jl_IndexOutOfBoundsException {
}
var $d_jl_ArrayIndexOutOfBoundsException = new $TypeData().i($c_jl_ArrayIndexOutOfBoundsException, "java.lang.ArrayIndexOutOfBoundsException", ({
  dn: 1,
  bu: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
function $f_jl_Double__equals__O__Z($thiz, that) {
  return Object.is($thiz, that);
}
function $f_jl_Double__hashCode__I($thiz) {
  var valueInt = ($thiz | 0);
  if (((valueInt === $thiz) && ((1.0 / $thiz) !== (-Infinity)))) {
    return valueInt;
  } else if (($thiz !== $thiz)) {
    return 2146959360;
  } else {
    var fpBitsDataView = $fpBitsDataView;
    fpBitsDataView.setFloat64(0, $thiz, true);
    return ((fpBitsDataView.getInt32(0, true) | 0) ^ (fpBitsDataView.getInt32(4, true) | 0));
  }
}
function $f_jl_Double__toString__T($thiz) {
  return ("" + $thiz);
}
function $f_jl_Double__compareTo__O__I($thiz, o) {
  return $m_jl_Double$().kq($thiz, o);
}
function $isArrayOf_jl_Double(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.br)));
}
var $d_jl_Double = new $TypeData().i(0, "java.lang.Double", ({
  br: 1,
  ai: 1,
  a: 1,
  F: 1,
  a5: 1,
  az: 1
}), ((x) => ((typeof x) === "number")));
function $f_jl_Float__equals__O__Z($thiz, that) {
  return Object.is($thiz, that);
}
function $f_jl_Float__hashCode__I($thiz) {
  var value = $thiz;
  var valueInt = (value | 0);
  if (((valueInt === value) && ((1.0 / value) !== (-Infinity)))) {
    return valueInt;
  } else if ((value !== value)) {
    return 2146959360;
  } else {
    var fpBitsDataView = $fpBitsDataView;
    fpBitsDataView.setFloat64(0, value, true);
    return ((fpBitsDataView.getInt32(0, true) | 0) ^ (fpBitsDataView.getInt32(4, true) | 0));
  }
}
function $f_jl_Float__toString__T($thiz) {
  return ("" + $thiz);
}
function $f_jl_Float__compareTo__O__I($thiz, o) {
  return $m_jl_Double$().kq($thiz, o);
}
function $isArrayOf_jl_Float(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bs)));
}
var $d_jl_Float = new $TypeData().i(0, "java.lang.Float", ({
  bs: 1,
  ai: 1,
  a: 1,
  F: 1,
  a5: 1,
  az: 1
}), ((x) => $isFloat(x)));
function $f_jl_Integer__equals__O__Z($thiz, that) {
  return Object.is($thiz, that);
}
function $f_jl_Integer__hashCode__I($thiz) {
  return $thiz;
}
function $f_jl_Integer__toString__T($thiz) {
  return ("" + $thiz);
}
function $f_jl_Integer__compareTo__O__I($thiz, o) {
  return (($thiz === o) ? 0 : (($thiz < o) ? (-1) : 1));
}
function $isArrayOf_jl_Integer(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bv)));
}
var $d_jl_Integer = new $TypeData().i(0, "java.lang.Integer", ({
  bv: 1,
  ai: 1,
  a: 1,
  F: 1,
  a5: 1,
  az: 1
}), ((x) => $isInt(x)));
function $f_jl_Long__equals__O__Z($thiz, $thizhi, that) {
  if ((that instanceof $Long)) {
    var $x_1 = that;
    var this$1_$_lo = $x_1.l;
    var this$1_$_hi = $x_1.h;
    return ((($thiz ^ this$1_$_lo) | ($thizhi ^ this$1_$_hi)) === 0);
  } else {
    return false;
  }
}
function $f_jl_Long__hashCode__I($thiz, $thizhi) {
  return ($thiz ^ $thizhi);
}
function $f_jl_Long__toString__T($thiz, $thizhi) {
  return $m_RTLong$().lb($thiz, $thizhi);
}
function $f_jl_Long__compareTo__O__I($thiz, $thizhi, o) {
  var $x_1 = o;
  var this$1_$_lo = $x_1.l;
  var this$1_$_hi = $x_1.h;
  return (($thizhi === this$1_$_hi) ? (($thiz === this$1_$_lo) ? 0 : ((($thiz >>> 0) < (this$1_$_lo >>> 0)) ? (-1) : 1)) : (($thizhi < this$1_$_hi) ? (-1) : 1));
}
function $isArrayOf_jl_Long(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bw)));
}
var $d_jl_Long = new $TypeData().i(0, "java.lang.Long", ({
  bw: 1,
  ai: 1,
  a: 1,
  F: 1,
  a5: 1,
  az: 1
}), ((x) => (x instanceof $Long)));
class $c_jl_NumberFormatException extends $c_jl_IllegalArgumentException {
  constructor(s) {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, s, null, true, true);
  }
}
var $d_jl_NumberFormatException = new $TypeData().i($c_jl_NumberFormatException, "java.lang.NumberFormatException", ({
  dC: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
function $f_T__codePointAt__I__I($thiz, index) {
  return ($thiz.codePointAt(index) | 0);
}
function $f_T__hashCode__I($thiz) {
  var n = $thiz.length;
  var h = 0;
  var i = 0;
  while ((i !== n)) {
    h = (((((h << 5) - h) | 0) + $thiz.charCodeAt(i)) | 0);
    i = ((1 + i) | 0);
  }
  return h;
}
function $f_T__equals__O__Z($thiz, that) {
  return ($thiz === that);
}
function $f_T__compareTo__T__I($thiz, anotherString) {
  var thisLength = $thiz.length;
  var strLength = anotherString.length;
  var minLength = ((thisLength < strLength) ? thisLength : strLength);
  var i = 0;
  while ((i !== minLength)) {
    var cmp = (($thiz.charCodeAt(i) - anotherString.charCodeAt(i)) | 0);
    if ((cmp !== 0)) {
      return cmp;
    }
    i = ((1 + i) | 0);
  }
  return ((thisLength - strLength) | 0);
}
function $f_T__getChars__I__I__AC__I__V($thiz, srcBegin, srcEnd, dst, dstBegin) {
  var offset = ((dstBegin - srcBegin) | 0);
  var i = srcBegin;
  while ((i < srcEnd)) {
    dst.a[((i + offset) | 0)] = $thiz.charCodeAt(i);
    i = ((1 + i) | 0);
  }
}
function $f_T__indexOf__I__I($thiz, ch) {
  var str = $m_jl_Character$().hW(ch);
  return ($thiz.indexOf(str) | 0);
}
function $f_T__repeat__I__T($thiz, count) {
  if ((count < 0)) {
    throw $ct_jl_IllegalArgumentException__(new $c_jl_IllegalArgumentException());
  } else {
    return $thiz.repeat(count);
  }
}
function $f_T__split__T__I__AT($thiz, regex, limit) {
  return $m_ju_regex_PatternCompiler$().nX(regex, 0).rm($thiz, limit);
}
function $f_T__trim__T($thiz) {
  var len = $thiz.length;
  var start = 0;
  while (((start !== len) && ($thiz.charCodeAt(start) <= 32))) {
    start = ((1 + start) | 0);
  }
  if ((start === len)) {
    return "";
  } else {
    var end = len;
    while (($thiz.charCodeAt(((end - 1) | 0)) <= 32)) {
      end = ((end - 1) | 0);
    }
    if (((start | (end ^ len)) === 0)) {
      return $thiz;
    } else {
      var beginIndex = start;
      var endIndex = end;
      return $thiz.substring(beginIndex, endIndex);
    }
  }
}
function $f_T__toString__T($thiz) {
  return $thiz;
}
function $f_T__compareTo__O__I($thiz, o) {
  return $f_T__compareTo__T__I($thiz, o);
}
function $isArrayOf_T(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bz)));
}
var $d_T = new $TypeData().i(0, "java.lang.String", ({
  bz: 1,
  a: 1,
  F: 1,
  aQ: 1,
  a5: 1,
  az: 1
}), ((x) => ((typeof x) === "string")));
class $c_ju_FormatterClosedException extends $c_jl_IllegalStateException {
  constructor() {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
}
var $d_ju_FormatterClosedException = new $TypeData().i($c_ju_FormatterClosedException, "java.util.FormatterClosedException", ({
  e3: 1,
  bt: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_IllegalFormatException extends $c_jl_IllegalArgumentException {
}
class $c_ju_regex_PatternSyntaxException extends $c_jl_IllegalArgumentException {
  constructor(desc, regex, index) {
    super();
    this.mn = null;
    this.mp = null;
    this.mo = 0;
    this.mn = desc;
    this.mp = regex;
    this.mo = index;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    var idx = this.mo;
    var re = this.mp;
    var indexHint = ((idx < 0) ? "" : (" near index " + idx));
    var base = (((this.mn + indexHint) + "\n") + re);
    return ((((idx >= 0) && (re !== null)) && (idx < re.length)) ? (((base + "\n") + $f_T__repeat__I__T(" ", idx)) + "^") : base);
  }
}
var $d_ju_regex_PatternSyntaxException = new $TypeData().i($c_ju_regex_PatternSyntaxException, "java.util.regex.PatternSyntaxException", ({
  ez: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
/** @constructor */
function $c_s_None$() {
}
$p = $c_s_None$.prototype = new $h_s_Option();
$p.constructor = $c_s_None$;
/** @constructor */
function $h_s_None$() {
}
$h_s_None$.prototype = $p;
$p.u = (function() {
  return 2433880;
});
$p.t = (function() {
  return "None";
});
$p.bE = (function() {
  return 0;
});
$p.bG = (function() {
  return "None";
});
$p.bF = (function(n) {
  throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
});
$p.r2 = (function() {
  throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), "None.get");
});
$p.S = (function() {
  this.r2();
});
var $d_s_None$ = new $TypeData().i($c_s_None$, "scala.None$", ({
  eL: 1,
  bD: 1,
  b: 1,
  d: 1,
  J: 1,
  a: 1
}));
var $n_s_None$;
function $m_s_None$() {
  if ((!$n_s_None$)) {
    $n_s_None$ = new $c_s_None$();
  }
  return $n_s_None$;
}
/** @constructor */
function $c_s_Some(value) {
  this.cf = null;
  this.cf = value;
}
$p = $c_s_Some.prototype = new $h_s_Option();
$p.constructor = $c_s_Some;
/** @constructor */
function $h_s_Some() {
}
$h_s_Some.prototype = $p;
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().dr(this, 1323286827, true);
});
$p.p = (function(x$0) {
  return ((this === x$0) || ((x$0 instanceof $c_s_Some) && $m_sr_BoxesRunTime$().n(this.cf, x$0.cf)));
});
$p.t = (function() {
  return $m_sr_ScalaRunTime$().dL(this);
});
$p.bE = (function() {
  return 1;
});
$p.bG = (function() {
  return "Some";
});
$p.bF = (function(n) {
  if ((n === 0)) {
    return this.cf;
  }
  throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
});
$p.S = (function() {
  return this.cf;
});
function $isArrayOf_s_Some(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bF)));
}
var $d_s_Some = new $TypeData().i($c_s_Some, "scala.Some", ({
  bF: 1,
  bD: 1,
  b: 1,
  d: 1,
  J: 1,
  a: 1
}));
/** @constructor */
function $c_sc_AbstractIterable() {
}
$p = $c_sc_AbstractIterable.prototype = new $h_O();
$p.constructor = $c_sc_AbstractIterable;
/** @constructor */
function $h_sc_AbstractIterable() {
}
$h_sc_AbstractIterable.prototype = $p;
$p.q = (function() {
  return (-1);
});
$p.dO = (function(f) {
  $f_sc_IterableOnceOps__foreach__F1__V(this, f);
});
$p.dN = (function(p) {
  return $f_sc_IterableOnceOps__forall__F1__Z(this, p);
});
$p.fC = (function(p) {
  return $f_sc_IterableOnceOps__exists__F1__Z(this, p);
});
$p.hG = (function(p) {
  return $f_sc_IterableOnceOps__find__F1__s_Option(this, p);
});
$p.d4 = (function(z, op) {
  return $f_sc_IterableOnceOps__foldLeft__O__F2__O(this, z, op);
});
$p.g = (function() {
  return $f_sc_IterableOnceOps__isEmpty__Z(this);
});
$p.bc = (function(dest, start, n) {
  return $f_sc_IterableOnceOps__copyToArray__O__I__I__I(this, dest, start, n);
});
$p.cl = (function(b, start, sep, end) {
  return $f_sc_IterableOnceOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder(this, b, start, sep, end);
});
$p.c2 = (function() {
  $m_sci_List$();
  return $m_sci_Nil$().cc(this);
});
$p.d6 = (function() {
  return $f_sc_IterableOnceOps__reversed__sc_Iterable(this);
});
$p.m = (function() {
  return this.c().f();
});
$p.aa = (function(f) {
  return $f_sc_IterableOps__map__F1__O(this, f);
});
$p.gr = (function(coll) {
  return this.aH().a3(coll);
});
$p.cI = (function() {
  return this.aH().T();
});
$p.bK = (function() {
  return this.bl();
});
$p.en = (function(coll) {
  return this.gr(coll);
});
/** @constructor */
function $c_sc_ArrayOps$ArrayIterator(xs) {
  this.id = null;
  this.e2 = 0;
  this.gR = 0;
  this.id = xs;
  this.e2 = 0;
  this.gR = $m_jl_reflect_Array$().b8(this.id);
}
$p = $c_sc_ArrayOps$ArrayIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_ArrayOps$ArrayIterator;
/** @constructor */
function $h_sc_ArrayOps$ArrayIterator() {
}
$h_sc_ArrayOps$ArrayIterator.prototype = $p;
$p.q = (function() {
  return ((this.gR - this.e2) | 0);
});
$p.k = (function() {
  return (this.e2 < this.gR);
});
$p.f = (function() {
  if ((this.e2 >= $m_jl_reflect_Array$().b8(this.id))) {
    $m_sc_Iterator$().G.f();
  }
  var r = $m_sr_ScalaRunTime$().aM(this.id, this.e2);
  this.e2 = ((1 + this.e2) | 0);
  return r;
});
$p.cE = (function(n) {
  if ((n > 0)) {
    var newPos = ((this.e2 + n) | 0);
    if ((newPos < 0)) {
      var $x_1 = this.gR;
    } else {
      var a = this.gR;
      var $x_1 = ((a < newPos) ? a : newPos);
    }
    this.e2 = $x_1;
  }
  return this;
});
var $d_sc_ArrayOps$ArrayIterator = new $TypeData().i($c_sc_ArrayOps$ArrayIterator, "scala.collection.ArrayOps$ArrayIterator", ({
  f0: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1,
  a: 1
}));
function $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__($thiz, self) {
  $thiz.jF = self;
  $thiz.e3 = 0;
  $thiz.br = self.l();
  return $thiz;
}
function $p_sc_IndexedSeqView$IndexedSeqViewIterator__formatRange$1__I__I($thiz, value) {
  return ((value < 0) ? 0 : ((value > $thiz.br) ? $thiz.br : value));
}
/** @constructor */
function $c_sc_IndexedSeqView$IndexedSeqViewIterator() {
  this.jF = null;
  this.e3 = 0;
  this.br = 0;
}
$p = $c_sc_IndexedSeqView$IndexedSeqViewIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_IndexedSeqView$IndexedSeqViewIterator;
/** @constructor */
function $h_sc_IndexedSeqView$IndexedSeqViewIterator() {
}
$h_sc_IndexedSeqView$IndexedSeqViewIterator.prototype = $p;
$p.q = (function() {
  return this.br;
});
$p.k = (function() {
  return (this.br > 0);
});
$p.f = (function() {
  if ((this.br > 0)) {
    var r = this.jF.r(this.e3);
    this.e3 = ((1 + this.e3) | 0);
    this.br = ((this.br - 1) | 0);
    return r;
  } else {
    return $m_sc_Iterator$().G.f();
  }
});
$p.cE = (function(n) {
  if ((n > 0)) {
    this.e3 = ((this.e3 + n) | 0);
    var b = ((this.br - n) | 0);
    this.br = ((b < 0) ? 0 : b);
  }
  return this;
});
$p.gy = (function(from, until) {
  var formatFrom = $p_sc_IndexedSeqView$IndexedSeqViewIterator__formatRange$1__I__I(this, from);
  var formatUntil = $p_sc_IndexedSeqView$IndexedSeqViewIterator__formatRange$1__I__I(this, until);
  var b = ((formatUntil - formatFrom) | 0);
  this.br = ((b < 0) ? 0 : b);
  this.e3 = ((this.e3 + formatFrom) | 0);
  return this;
});
var $d_sc_IndexedSeqView$IndexedSeqViewIterator = new $TypeData().i($c_sc_IndexedSeqView$IndexedSeqViewIterator, "scala.collection.IndexedSeqView$IndexedSeqViewIterator", ({
  bM: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1,
  a: 1
}));
function $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__($thiz, self) {
  $thiz.jG = self;
  $thiz.bJ = self.l();
  $thiz.fd = (($thiz.bJ - 1) | 0);
  return $thiz;
}
/** @constructor */
function $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator() {
  this.jG = null;
  this.bJ = 0;
  this.fd = 0;
}
$p = $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator;
/** @constructor */
function $h_sc_IndexedSeqView$IndexedSeqViewReverseIterator() {
}
$h_sc_IndexedSeqView$IndexedSeqViewReverseIterator.prototype = $p;
$p.k = (function() {
  return (this.bJ > 0);
});
$p.f = (function() {
  if ((this.bJ > 0)) {
    var r = this.jG.r(this.fd);
    this.fd = ((this.fd - 1) | 0);
    this.bJ = ((this.bJ - 1) | 0);
    return r;
  } else {
    return $m_sc_Iterator$().G.f();
  }
});
$p.gy = (function(from, until) {
  if ((this.bJ > 0)) {
    if ((this.bJ <= from)) {
      this.bJ = 0;
    } else if ((from <= 0)) {
      if (((until >= 0) && (until < this.bJ))) {
        this.bJ = until;
      }
    } else {
      this.fd = ((this.fd - from) | 0);
      if (((until >= 0) && (until < this.bJ))) {
        if ((until <= from)) {
          this.bJ = 0;
        } else {
          this.bJ = ((until - from) | 0);
        }
      } else {
        this.bJ = ((this.bJ - from) | 0);
      }
    }
  }
  return this;
});
var $d_sc_IndexedSeqView$IndexedSeqViewReverseIterator = new $TypeData().i($c_sc_IndexedSeqView$IndexedSeqViewReverseIterator, "scala.collection.IndexedSeqView$IndexedSeqViewReverseIterator", ({
  bN: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1,
  a: 1
}));
/** @constructor */
function $c_sc_Iterator$$anon$21() {
  this.hg = null;
  $ct_scm_ImmutableBuilder__sc_IterableOnce__(this, $m_sc_Iterator$().G);
}
$p = $c_sc_Iterator$$anon$21.prototype = new $h_scm_ImmutableBuilder();
$p.constructor = $c_sc_Iterator$$anon$21;
/** @constructor */
function $h_sc_Iterator$$anon$21() {
}
$h_sc_Iterator$$anon$21.prototype = $p;
$p.pP = (function(elem) {
  this.hg = this.hg.fB(new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => new $c_sc_Iterator$$anon$20(elem))));
  return this;
});
$p.aq = (function(elem) {
  return this.pP(elem);
});
var $d_sc_Iterator$$anon$21 = new $TypeData().i($c_sc_Iterator$$anon$21, "scala.collection.Iterator$$anon$21", ({
  fd: 1,
  h8: 1,
  H: 1,
  I: 1,
  K: 1,
  a7: 1
}));
/** @constructor */
function $c_sc_Iterator$$anon$7(pf$2, outer) {
  this.mC = null;
  this.mB = null;
  this.g8 = 0;
  this.jI = null;
  this.mC = pf$2;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.jI = outer;
  this.g8 = 0;
}
$p = $c_sc_Iterator$$anon$7.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sc_Iterator$$anon$7;
/** @constructor */
function $h_sc_Iterator$$anon$7() {
}
$h_sc_Iterator$$anon$7.prototype = $p;
$p.t = (function() {
  return "<function1>";
});
$p.d = (function(value) {
  return $m_sr_Statics$PFMarker$();
});
$p.k = (function() {
  var marker = $m_sr_Statics$PFMarker$();
  while ((this.g8 === 0)) {
    if (this.jI.k()) {
      var x = this.jI.f();
      var v = this.mC.bQ(x, this);
      if ((marker !== v)) {
        this.mB = v;
        this.g8 = 1;
      }
    } else {
      this.g8 = (-1);
    }
  }
  return (this.g8 === 1);
});
$p.f = (function() {
  if (this.k()) {
    this.g8 = 0;
    return this.mB;
  } else {
    return $m_sc_Iterator$().G.f();
  }
});
var $d_sc_Iterator$$anon$7 = new $TypeData().i($c_sc_Iterator$$anon$7, "scala.collection.Iterator$$anon$7", ({
  fe: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1,
  i: 1
}));
function $f_sc_MapOps__getOrElse__O__F0__O($thiz, key, default$1) {
  var x5 = $thiz.cn(key);
  if ((x5 instanceof $c_s_Some)) {
    return x5.cf;
  }
  if (($m_s_None$() === x5)) {
    return default$1.a2();
  }
  throw new $c_s_MatchError(x5);
}
function $f_sc_MapOps__apply__O__O($thiz, key) {
  var x8 = $thiz.cn(key);
  if (($m_s_None$() === x8)) {
    return $f_sc_MapOps__default__O__O($thiz, key);
  }
  if ((x8 instanceof $c_s_Some)) {
    return x8.cf;
  }
  throw new $c_s_MatchError(x8);
}
function $f_sc_MapOps__applyOrElse__O__F1__O($thiz, x, default$1) {
  return $thiz.eo(x, new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => default$1.d(x))));
}
function $f_sc_MapOps__foreachEntry__F2__V($thiz, f) {
  var it = $thiz.c();
  while (it.k()) {
    var next = it.f();
    f.ca(next.aw(), next.ap());
  }
}
function $f_sc_MapOps__default__O__O($thiz, key) {
  throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), ("key not found: " + key));
}
function $f_sc_MapOps__contains__O__Z($thiz, key) {
  return (!$thiz.cn(key).g());
}
function $f_sc_MapOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder($thiz, sb, start, sep, end) {
  return $f_sc_IterableOnceOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder(new $c_sc_Iterator$$anon$9(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$1$2) => {
    var k = x$1$2.aw();
    var v = x$1$2.ap();
    return ((k + " -> ") + v);
  })), $thiz.c()), sb, start, sep, end);
}
function $f_sc_StrictOptimizedSeqOps__distinctBy__F1__O($thiz, f) {
  var builder = $thiz.cI();
  var seen = $ct_scm_HashSet__(new $c_scm_HashSet());
  var it = $thiz.c();
  while (it.k()) {
    var next = it.f();
    if (seen.iN(f.d(next))) {
      builder.aq(next);
    }
  }
  return builder.aU();
}
function $f_sc_StrictOptimizedSeqOps__prepended__O__O($thiz, elem) {
  var b = $thiz.ep().T();
  $f_scm_Builder__sizeHint__sc_IterableOnce__I__V(b, $thiz, 1);
  b.aq(elem);
  b.b7($thiz);
  return b.aU();
}
function $f_sc_StrictOptimizedSeqOps__appendedAll__sc_IterableOnce__O($thiz, suffix) {
  var b = $thiz.ep().T();
  b.b7($thiz);
  b.b7(suffix);
  return b.aU();
}
/** @constructor */
function $c_sci_ArraySeq$() {
  this.jP = null;
  $n_sci_ArraySeq$ = this;
  this.jP = new $c_sc_ClassTagSeqFactory$AnySeqDelegate(this);
}
$p = $c_sci_ArraySeq$.prototype = new $h_O();
$p.constructor = $c_sci_ArraySeq$;
/** @constructor */
function $h_sci_ArraySeq$() {
}
$h_sci_ArraySeq$.prototype = $p;
$p.kI = (function(it, tag) {
  return ((it instanceof $c_sci_ArraySeq) ? it : this.gB($m_s_Array$().oh(it, tag)));
});
$p.j2 = (function(evidence$1) {
  return new $c_scm_Builder$$anon$1(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((b$2) => this.gB($f_sc_IterableOnceOps__toArray__s_reflect_ClassTag__O(b$2, evidence$1)))), ($m_scm_ArrayBuffer$(), new $c_scm_ArrayBuffer$$anon$1()));
});
$p.gB = (function(x) {
  if ((x === null)) {
    return null;
  }
  if ((x instanceof $ac_O)) {
    return new $c_sci_ArraySeq$ofRef(x);
  }
  if ((x instanceof $ac_I)) {
    return new $c_sci_ArraySeq$ofInt(x);
  }
  if ((x instanceof $ac_D)) {
    return new $c_sci_ArraySeq$ofDouble(x);
  }
  if ((x instanceof $ac_J)) {
    return new $c_sci_ArraySeq$ofLong(x);
  }
  if ((x instanceof $ac_F)) {
    return new $c_sci_ArraySeq$ofFloat(x);
  }
  if ((x instanceof $ac_C)) {
    return new $c_sci_ArraySeq$ofChar(x);
  }
  if ((x instanceof $ac_B)) {
    return new $c_sci_ArraySeq$ofByte(x);
  }
  if ((x instanceof $ac_S)) {
    return new $c_sci_ArraySeq$ofShort(x);
  }
  if ((x instanceof $ac_Z)) {
    return new $c_sci_ArraySeq$ofBoolean(x);
  }
  if ($isArrayOf_jl_Void(x, 1)) {
    return new $c_sci_ArraySeq$ofUnit(x);
  }
  throw new $c_s_MatchError(x);
});
$p.kH = (function(it, evidence$1) {
  return this.kI(it, evidence$1);
});
var $d_sci_ArraySeq$ = new $TypeData().i($c_sci_ArraySeq$, "scala.collection.immutable.ArraySeq$", ({
  fB: 1,
  a: 1,
  bL: 1,
  bJ: 1,
  bK: 1,
  bW: 1
}));
var $n_sci_ArraySeq$;
function $m_sci_ArraySeq$() {
  if ((!$n_sci_ArraySeq$)) {
    $n_sci_ArraySeq$ = new $c_sci_ArraySeq$();
  }
  return $n_sci_ArraySeq$;
}
/** @constructor */
function $c_sci_HashMapBuilder$$anon$1(hm$1, outer) {
  this.aK = 0;
  this.ey = 0;
  this.cj = null;
  this.bM = 0;
  this.cR = null;
  this.ez = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $ct_sci_ChampBaseIterator__sci_Node__(this, hm$1.bN);
  while (this.k()) {
    var originalHash = this.cj.co(this.aK);
    outer.gC(outer.dB, this.cj.dP(this.aK), this.cj.dp(this.aK), originalHash, $m_sc_Hashing$().bD(originalHash), 0);
    this.aK = ((1 + this.aK) | 0);
  }
}
$p = $c_sci_HashMapBuilder$$anon$1.prototype = new $h_sci_ChampBaseIterator();
$p.constructor = $c_sci_HashMapBuilder$$anon$1;
/** @constructor */
function $h_sci_HashMapBuilder$$anon$1() {
}
$h_sci_HashMapBuilder$$anon$1.prototype = $p;
$p.f = (function() {
  return $m_sc_Iterator$().G.f();
});
var $d_sci_HashMapBuilder$$anon$1 = new $TypeData().i($c_sci_HashMapBuilder$$anon$1, "scala.collection.immutable.HashMapBuilder$$anon$1", ({
  fG: 1,
  aE: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sci_HashSetBuilder$$anon$1(hm$1, outer) {
  this.aK = 0;
  this.ey = 0;
  this.cj = null;
  this.bM = 0;
  this.cR = null;
  this.ez = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $ct_sci_ChampBaseIterator__sci_Node__(this, hm$1.c4);
  while (this.k()) {
    var originalHash = this.cj.co(this.aK);
    outer.lc(outer.fk, this.cj.dR(this.aK), originalHash, $m_sc_Hashing$().bD(originalHash), 0);
    this.aK = ((1 + this.aK) | 0);
  }
}
$p = $c_sci_HashSetBuilder$$anon$1.prototype = new $h_sci_ChampBaseIterator();
$p.constructor = $c_sci_HashSetBuilder$$anon$1;
/** @constructor */
function $h_sci_HashSetBuilder$$anon$1() {
}
$h_sci_HashSetBuilder$$anon$1.prototype = $p;
$p.f = (function() {
  return $m_sc_Iterator$().G.f();
});
var $d_sci_HashSetBuilder$$anon$1 = new $TypeData().i($c_sci_HashSetBuilder$$anon$1, "scala.collection.immutable.HashSetBuilder$$anon$1", ({
  fJ: 1,
  aE: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
function $is_sci_Iterable(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.q)));
}
function $isArrayOf_sci_Iterable(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.q)));
}
/** @constructor */
function $c_sci_Map$Map2$$anon$1(outer) {
  this.eD = 0;
  this.gg = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $ct_sci_Map$Map2$Map2Iterator__sci_Map$Map2__(this, outer);
}
$p = $c_sci_Map$Map2$$anon$1.prototype = new $h_sci_Map$Map2$Map2Iterator();
$p.constructor = $c_sci_Map$Map2$$anon$1;
/** @constructor */
function $h_sci_Map$Map2$$anon$1() {
}
$h_sci_Map$Map2$$anon$1.prototype = $p;
var $d_sci_Map$Map2$$anon$1 = new $TypeData().i($c_sci_Map$Map2$$anon$1, "scala.collection.immutable.Map$Map2$$anon$1", ({
  g0: 1,
  g1: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sci_Map$Map3$$anon$4(outer) {
  this.eF = 0;
  this.eE = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $ct_sci_Map$Map3$Map3Iterator__sci_Map$Map3__(this, outer);
}
$p = $c_sci_Map$Map3$$anon$4.prototype = new $h_sci_Map$Map3$Map3Iterator();
$p.constructor = $c_sci_Map$Map3$$anon$4;
/** @constructor */
function $h_sci_Map$Map3$$anon$4() {
}
$h_sci_Map$Map3$$anon$4.prototype = $p;
var $d_sci_Map$Map3$$anon$4 = new $TypeData().i($c_sci_Map$Map3$$anon$4, "scala.collection.immutable.Map$Map3$$anon$4", ({
  g2: 1,
  g3: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sci_Map$Map4$$anon$7(outer) {
  this.eG = 0;
  this.dG = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $ct_sci_Map$Map4$Map4Iterator__sci_Map$Map4__(this, outer);
}
$p = $c_sci_Map$Map4$$anon$7.prototype = new $h_sci_Map$Map4$Map4Iterator();
$p.constructor = $c_sci_Map$Map4$$anon$7;
/** @constructor */
function $h_sci_Map$Map4$$anon$7() {
}
$h_sci_Map$Map4$$anon$7.prototype = $p;
var $d_sci_Map$Map4$$anon$7 = new $TypeData().i($c_sci_Map$Map4$$anon$7, "scala.collection.immutable.Map$Map4$$anon$7", ({
  g4: 1,
  g5: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sci_MapKeyValueTupleHashIterator(rootNode) {
  this.eA = 0;
  this.ir = null;
  this.cx = 0;
  this.h2 = null;
  this.h3 = null;
  this.jR = 0;
  this.mV = null;
  $ct_sci_ChampBaseReverseIterator__sci_Node__(this, rootNode);
  this.jR = 0;
}
$p = $c_sci_MapKeyValueTupleHashIterator.prototype = new $h_sci_ChampBaseReverseIterator();
$p.constructor = $c_sci_MapKeyValueTupleHashIterator;
/** @constructor */
function $h_sci_MapKeyValueTupleHashIterator() {
}
$h_sci_MapKeyValueTupleHashIterator.prototype = $p;
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().p2(this.jR, $m_sr_Statics$().y(this.mV), (-889275714));
});
$p.rB = (function() {
  if ((!this.k())) {
    $m_sc_Iterator$().G.f();
  }
  this.jR = this.ir.co(this.eA);
  this.mV = this.ir.dp(this.eA);
  this.eA = ((this.eA - 1) | 0);
  return this;
});
$p.f = (function() {
  return this.rB();
});
var $d_sci_MapKeyValueTupleHashIterator = new $TypeData().i($c_sci_MapKeyValueTupleHashIterator, "scala.collection.immutable.MapKeyValueTupleHashIterator", ({
  g7: 1,
  fC: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sci_MapKeyValueTupleIterator(rootNode) {
  this.aK = 0;
  this.ey = 0;
  this.cj = null;
  this.bM = 0;
  this.cR = null;
  this.ez = null;
  $ct_sci_ChampBaseIterator__sci_Node__(this, rootNode);
}
$p = $c_sci_MapKeyValueTupleIterator.prototype = new $h_sci_ChampBaseIterator();
$p.constructor = $c_sci_MapKeyValueTupleIterator;
/** @constructor */
function $h_sci_MapKeyValueTupleIterator() {
}
$h_sci_MapKeyValueTupleIterator.prototype = $p;
$p.gw = (function() {
  if ((!this.k())) {
    $m_sc_Iterator$().G.f();
  }
  var payload = this.cj.op(this.aK);
  this.aK = ((1 + this.aK) | 0);
  return payload;
});
$p.f = (function() {
  return this.gw();
});
var $d_sci_MapKeyValueTupleIterator = new $TypeData().i($c_sci_MapKeyValueTupleIterator, "scala.collection.immutable.MapKeyValueTupleIterator", ({
  g8: 1,
  aE: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
function $p_sci_NewVectorIterator__advanceSlice__V($thiz) {
  if (($thiz.ck <= $thiz.b9)) {
    $m_sc_Iterator$().G.f();
  }
  $thiz.eI = ((1 + $thiz.eI) | 0);
  var slice = $thiz.jT.dX($thiz.eI);
  while ((slice.a.length === 0)) {
    $thiz.eI = ((1 + $thiz.eI) | 0);
    slice = $thiz.jT.dX($thiz.eI);
  }
  $thiz.h6 = $thiz.fn;
  var count = $thiz.mX;
  var idx = $thiz.eI;
  var c = (((count + ((count >>> 31) | 0)) | 0) >> 1);
  var a = ((idx - c) | 0);
  var sign = (a >> 31);
  $thiz.eH = ((((1 + c) | 0) - (((a ^ sign) - sign) | 0)) | 0);
  var x46 = $thiz.eH;
  switch (x46) {
    case 1: {
      $thiz.bu = slice;
      break;
    }
    case 2: {
      $thiz.bv = slice;
      break;
    }
    case 3: {
      $thiz.c9 = slice;
      break;
    }
    case 4: {
      $thiz.dg = slice;
      break;
    }
    case 5: {
      $thiz.fm = slice;
      break;
    }
    case 6: {
      $thiz.jS = slice;
      break;
    }
    default: {
      throw new $c_s_MatchError(x46);
    }
  }
  $thiz.fn = (($thiz.h6 + Math.imul(slice.a.length, (1 << Math.imul(5, (($thiz.eH - 1) | 0))))) | 0);
  if (($thiz.fn > $thiz.ec)) {
    $thiz.fn = $thiz.ec;
  }
  if (($thiz.eH > 1)) {
    $thiz.gh = (((1 << Math.imul(5, $thiz.eH)) - 1) | 0);
  }
}
function $p_sci_NewVectorIterator__advance__V($thiz) {
  var pos = (((($thiz.b9 - $thiz.ck) | 0) + $thiz.ec) | 0);
  if ((pos === $thiz.fn)) {
    $p_sci_NewVectorIterator__advanceSlice__V($thiz);
  }
  if (($thiz.eH > 1)) {
    var io = ((pos - $thiz.h6) | 0);
    $p_sci_NewVectorIterator__advanceA__I__I__V($thiz, io, ($thiz.gh ^ io));
    $thiz.gh = io;
  }
  $thiz.ck = (($thiz.ck - $thiz.b9) | 0);
  var a = $thiz.bu.a.length;
  var b = $thiz.ck;
  $thiz.eb = ((a < b) ? a : b);
  $thiz.b9 = 0;
}
function $p_sci_NewVectorIterator__advanceA__I__I__V($thiz, io, xor) {
  if ((xor < 1024)) {
    $thiz.bu = $thiz.bv.a[(31 & ((io >>> 5) | 0))];
  } else if ((xor < 32768)) {
    $thiz.bv = $thiz.c9.a[(31 & ((io >>> 10) | 0))];
    $thiz.bu = $thiz.bv.a[0];
  } else if ((xor < 1048576)) {
    $thiz.c9 = $thiz.dg.a[(31 & ((io >>> 15) | 0))];
    $thiz.bv = $thiz.c9.a[0];
    $thiz.bu = $thiz.bv.a[0];
  } else if ((xor < 33554432)) {
    $thiz.dg = $thiz.fm.a[(31 & ((io >>> 20) | 0))];
    $thiz.c9 = $thiz.dg.a[0];
    $thiz.bv = $thiz.c9.a[0];
    $thiz.bu = $thiz.bv.a[0];
  } else {
    $thiz.fm = $thiz.jS.a[((io >>> 25) | 0)];
    $thiz.dg = $thiz.fm.a[0];
    $thiz.c9 = $thiz.dg.a[0];
    $thiz.bv = $thiz.c9.a[0];
    $thiz.bu = $thiz.bv.a[0];
  }
}
function $p_sci_NewVectorIterator__setA__I__I__V($thiz, io, xor) {
  if ((xor < 1024)) {
    $thiz.bu = $thiz.bv.a[(31 & ((io >>> 5) | 0))];
  } else if ((xor < 32768)) {
    $thiz.bv = $thiz.c9.a[(31 & ((io >>> 10) | 0))];
    $thiz.bu = $thiz.bv.a[(31 & ((io >>> 5) | 0))];
  } else if ((xor < 1048576)) {
    $thiz.c9 = $thiz.dg.a[(31 & ((io >>> 15) | 0))];
    $thiz.bv = $thiz.c9.a[(31 & ((io >>> 10) | 0))];
    $thiz.bu = $thiz.bv.a[(31 & ((io >>> 5) | 0))];
  } else if ((xor < 33554432)) {
    $thiz.dg = $thiz.fm.a[(31 & ((io >>> 20) | 0))];
    $thiz.c9 = $thiz.dg.a[(31 & ((io >>> 15) | 0))];
    $thiz.bv = $thiz.c9.a[(31 & ((io >>> 10) | 0))];
    $thiz.bu = $thiz.bv.a[(31 & ((io >>> 5) | 0))];
  } else {
    $thiz.fm = $thiz.jS.a[((io >>> 25) | 0)];
    $thiz.dg = $thiz.fm.a[(31 & ((io >>> 20) | 0))];
    $thiz.c9 = $thiz.dg.a[(31 & ((io >>> 15) | 0))];
    $thiz.bv = $thiz.c9.a[(31 & ((io >>> 10) | 0))];
    $thiz.bu = $thiz.bv.a[(31 & ((io >>> 5) | 0))];
  }
}
/** @constructor */
function $c_sci_NewVectorIterator(v, totalLength, sliceCount) {
  this.jT = null;
  this.ec = 0;
  this.mX = 0;
  this.bu = null;
  this.bv = null;
  this.c9 = null;
  this.dg = null;
  this.fm = null;
  this.jS = null;
  this.eb = 0;
  this.b9 = 0;
  this.gh = 0;
  this.ck = 0;
  this.eI = 0;
  this.eH = 0;
  this.h6 = 0;
  this.fn = 0;
  this.jT = v;
  this.ec = totalLength;
  this.mX = sliceCount;
  this.bu = v.e;
  this.eb = this.bu.a.length;
  this.b9 = 0;
  this.gh = 0;
  this.ck = this.ec;
  this.eI = 0;
  this.eH = 1;
  this.h6 = 0;
  this.fn = this.eb;
}
$p = $c_sci_NewVectorIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sci_NewVectorIterator;
/** @constructor */
function $h_sci_NewVectorIterator() {
}
$h_sci_NewVectorIterator.prototype = $p;
$p.q = (function() {
  return ((this.ck - this.b9) | 0);
});
$p.k = (function() {
  return (this.ck > this.b9);
});
$p.f = (function() {
  if ((this.b9 === this.eb)) {
    $p_sci_NewVectorIterator__advance__V(this);
  }
  var r = this.bu.a[this.b9];
  this.b9 = ((1 + this.b9) | 0);
  return r;
});
$p.cE = (function(n) {
  if ((n > 0)) {
    var oldpos = ((((this.b9 - this.ck) | 0) + this.ec) | 0);
    var a = ((oldpos + n) | 0);
    var b = this.ec;
    var newpos = ((a < b) ? a : b);
    if ((newpos === this.ec)) {
      this.b9 = 0;
      this.ck = 0;
      this.eb = 0;
    } else {
      while ((newpos >= this.fn)) {
        $p_sci_NewVectorIterator__advanceSlice__V(this);
      }
      var io = ((newpos - this.h6) | 0);
      if ((this.eH > 1)) {
        $p_sci_NewVectorIterator__setA__I__I__V(this, io, (this.gh ^ io));
        this.gh = io;
      }
      this.eb = this.bu.a.length;
      this.b9 = (31 & io);
      this.ck = ((this.b9 + ((this.ec - newpos) | 0)) | 0);
      if ((this.eb > this.ck)) {
        this.eb = this.ck;
      }
    }
  }
  return this;
});
$p.bc = (function(xs, start, len) {
  var xsLen = $m_jl_reflect_Array$().b8(xs);
  var srcLen = ((this.ck - this.b9) | 0);
  var limit = ((len < srcLen) ? len : srcLen);
  var capacity = ((start < 0) ? xsLen : ((xsLen - start) | 0));
  var total = ((capacity < limit) ? capacity : limit);
  var total$1 = ((total < 0) ? 0 : total);
  var copied = 0;
  var isBoxed = (xs instanceof $ac_O);
  while ((copied < total$1)) {
    if ((this.b9 === this.eb)) {
      $p_sci_NewVectorIterator__advance__V(this);
    }
    var a = ((total$1 - copied) | 0);
    var b = ((this.bu.a.length - this.b9) | 0);
    var count = ((a < b) ? a : b);
    if (isBoxed) {
      var src = this.bu;
      var srcPos = this.b9;
      var destPos = ((start + copied) | 0);
      src.s(srcPos, xs, destPos, count);
    } else {
      $m_s_Array$().bT(this.bu, this.b9, xs, ((start + copied) | 0), count);
    }
    this.b9 = ((this.b9 + count) | 0);
    copied = ((copied + count) | 0);
  }
  return total$1;
});
var $d_sci_NewVectorIterator = new $TypeData().i($c_sci_NewVectorIterator, "scala.collection.immutable.NewVectorIterator", ({
  gb: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1,
  E: 1
}));
function $ct_sci_Set$SetNIterator__I__($thiz, n) {
  $thiz.ed = 0;
  $thiz.dH = n;
  return $thiz;
}
/** @constructor */
function $c_sci_Set$SetNIterator() {
  this.ed = 0;
  this.dH = 0;
}
$p = $c_sci_Set$SetNIterator.prototype = new $h_sc_AbstractIterator();
$p.constructor = $c_sci_Set$SetNIterator;
/** @constructor */
function $h_sci_Set$SetNIterator() {
}
$h_sci_Set$SetNIterator.prototype = $p;
$p.q = (function() {
  return this.dH;
});
$p.k = (function() {
  return (this.dH > 0);
});
$p.f = (function() {
  if (this.k()) {
    var r = this.r(this.ed);
    this.ed = ((1 + this.ed) | 0);
    this.dH = ((this.dH - 1) | 0);
    return r;
  } else {
    return $m_sc_Iterator$().G.f();
  }
});
$p.cE = (function(n) {
  if ((n > 0)) {
    this.ed = ((this.ed + n) | 0);
    var b = ((this.dH - n) | 0);
    this.dH = ((b < 0) ? 0 : b);
  }
  return this;
});
/** @constructor */
function $c_sci_SetHashIterator(rootNode) {
  this.aK = 0;
  this.ey = 0;
  this.cj = null;
  this.bM = 0;
  this.cR = null;
  this.ez = null;
  this.jU = 0;
  $ct_sci_ChampBaseIterator__sci_Node__(this, rootNode);
  this.jU = 0;
}
$p = $c_sci_SetHashIterator.prototype = new $h_sci_ChampBaseIterator();
$p.constructor = $c_sci_SetHashIterator;
/** @constructor */
function $h_sci_SetHashIterator() {
}
$h_sci_SetHashIterator.prototype = $p;
$p.u = (function() {
  return this.jU;
});
$p.f = (function() {
  if ((!this.k())) {
    $m_sc_Iterator$().G.f();
  }
  this.jU = this.cj.co(this.aK);
  this.aK = ((1 + this.aK) | 0);
  return this;
});
var $d_sci_SetHashIterator = new $TypeData().i($c_sci_SetHashIterator, "scala.collection.immutable.SetHashIterator", ({
  gp: 1,
  aE: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_sci_SetIterator(rootNode) {
  this.aK = 0;
  this.ey = 0;
  this.cj = null;
  this.bM = 0;
  this.cR = null;
  this.ez = null;
  $ct_sci_ChampBaseIterator__sci_Node__(this, rootNode);
}
$p = $c_sci_SetIterator.prototype = new $h_sci_ChampBaseIterator();
$p.constructor = $c_sci_SetIterator;
/** @constructor */
function $h_sci_SetIterator() {
}
$h_sci_SetIterator.prototype = $p;
$p.f = (function() {
  if ((!this.k())) {
    $m_sc_Iterator$().G.f();
  }
  var payload = this.cj.dR(this.aK);
  this.aK = ((1 + this.aK) | 0);
  return payload;
});
var $d_sci_SetIterator = new $TypeData().i($c_sci_SetIterator, "scala.collection.immutable.SetIterator", ({
  gq: 1,
  aE: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
function $ct_scm_ArrayBuilder__($thiz) {
  $thiz.jY = 0;
  $thiz.n5 = 0;
  return $thiz;
}
/** @constructor */
function $c_scm_ArrayBuilder() {
  this.jY = 0;
  this.n5 = 0;
}
$p = $c_scm_ArrayBuilder.prototype = new $h_O();
$p.constructor = $c_scm_ArrayBuilder;
/** @constructor */
function $h_scm_ArrayBuilder() {
}
$h_scm_ArrayBuilder.prototype = $p;
$p.aZ = (function(size) {
  if ((this.jY < size)) {
    this.rO(size);
  }
});
/** @constructor */
function $c_scm_ArraySeq$() {
  this.k0 = null;
  this.n7 = null;
  $n_scm_ArraySeq$ = this;
  this.k0 = new $c_sc_ClassTagSeqFactory$AnySeqDelegate(this);
  this.n7 = new $c_scm_ArraySeq$ofRef(new $ac_O(0));
}
$p = $c_scm_ArraySeq$.prototype = new $h_O();
$p.constructor = $c_scm_ArraySeq$;
/** @constructor */
function $h_scm_ArraySeq$() {
}
$h_scm_ArraySeq$.prototype = $p;
$p.qT = (function(it, evidence$1) {
  return this.j0($m_s_Array$().oh(it, evidence$1));
});
$p.j2 = (function(evidence$1) {
  return new $c_scm_Builder$$anon$1(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((x$2) => this.j0(x$2))), new $c_scm_ArrayBuilder$generic(evidence$1.bk()));
});
$p.j0 = (function(x) {
  if ((x === null)) {
    return null;
  }
  if ((x instanceof $ac_O)) {
    return new $c_scm_ArraySeq$ofRef(x);
  }
  if ((x instanceof $ac_I)) {
    return new $c_scm_ArraySeq$ofInt(x);
  }
  if ((x instanceof $ac_D)) {
    return new $c_scm_ArraySeq$ofDouble(x);
  }
  if ((x instanceof $ac_J)) {
    return new $c_scm_ArraySeq$ofLong(x);
  }
  if ((x instanceof $ac_F)) {
    return new $c_scm_ArraySeq$ofFloat(x);
  }
  if ((x instanceof $ac_C)) {
    return new $c_scm_ArraySeq$ofChar(x);
  }
  if ((x instanceof $ac_B)) {
    return new $c_scm_ArraySeq$ofByte(x);
  }
  if ((x instanceof $ac_S)) {
    return new $c_scm_ArraySeq$ofShort(x);
  }
  if ((x instanceof $ac_Z)) {
    return new $c_scm_ArraySeq$ofBoolean(x);
  }
  if ($isArrayOf_jl_Void(x, 1)) {
    return new $c_scm_ArraySeq$ofUnit(x);
  }
  throw new $c_s_MatchError(x);
});
$p.kH = (function(it, evidence$1) {
  return this.qT(it, evidence$1);
});
var $d_scm_ArraySeq$ = new $TypeData().i($c_scm_ArraySeq$, "scala.collection.mutable.ArraySeq$", ({
  gP: 1,
  a: 1,
  bL: 1,
  bJ: 1,
  bK: 1,
  bW: 1
}));
var $n_scm_ArraySeq$;
function $m_scm_ArraySeq$() {
  if ((!$n_scm_ArraySeq$)) {
    $n_scm_ArraySeq$ = new $c_scm_ArraySeq$();
  }
  return $n_scm_ArraySeq$;
}
/** @constructor */
function $c_scm_HashMap$$anon$1(outer) {
  this.eS = 0;
  this.ef = null;
  this.hd = 0;
  this.hc = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $ct_scm_HashMap$HashMapIterator__scm_HashMap__(this, outer);
}
$p = $c_scm_HashMap$$anon$1.prototype = new $h_scm_HashMap$HashMapIterator();
$p.constructor = $c_scm_HashMap$$anon$1;
/** @constructor */
function $h_scm_HashMap$$anon$1() {
}
$h_scm_HashMap$$anon$1.prototype = $p;
$p.kC = (function(nd) {
  return $ct_T2__O__O__(new $c_T2(), nd.eT, nd.cC);
});
var $d_scm_HashMap$$anon$1 = new $TypeData().i($c_scm_HashMap$$anon$1, "scala.collection.mutable.HashMap$$anon$1", ({
  gX: 1,
  b5: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_scm_HashMap$$anon$4(outer) {
  this.eS = 0;
  this.ef = null;
  this.hd = 0;
  this.hc = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $ct_scm_HashMap$HashMapIterator__scm_HashMap__(this, outer);
}
$p = $c_scm_HashMap$$anon$4.prototype = new $h_scm_HashMap$HashMapIterator();
$p.constructor = $c_scm_HashMap$$anon$4;
/** @constructor */
function $h_scm_HashMap$$anon$4() {
}
$h_scm_HashMap$$anon$4.prototype = $p;
$p.kC = (function(nd) {
  return nd;
});
var $d_scm_HashMap$$anon$4 = new $TypeData().i($c_scm_HashMap$$anon$4, "scala.collection.mutable.HashMap$$anon$4", ({
  gY: 1,
  b5: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_scm_HashMap$$anon$5(outer) {
  this.eS = 0;
  this.ef = null;
  this.hd = 0;
  this.hc = null;
  this.k2 = 0;
  this.pk = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.pk = outer;
  $ct_scm_HashMap$HashMapIterator__scm_HashMap__(this, outer);
  this.k2 = 0;
}
$p = $c_scm_HashMap$$anon$5.prototype = new $h_scm_HashMap$HashMapIterator();
$p.constructor = $c_scm_HashMap$$anon$5;
/** @constructor */
function $h_scm_HashMap$$anon$5() {
}
$h_scm_HashMap$$anon$5.prototype = $p;
$p.u = (function() {
  return this.k2;
});
$p.kC = (function(nd) {
  var $x_1 = $m_s_util_hashing_MurmurHash3$();
  var improvedHash = nd.dK;
  this.k2 = $x_1.d7((improvedHash ^ ((improvedHash >>> 16) | 0)), $m_sr_Statics$().y(nd.cC));
  return this;
});
var $d_scm_HashMap$$anon$5 = new $TypeData().i($c_scm_HashMap$$anon$5, "scala.collection.mutable.HashMap$$anon$5", ({
  gZ: 1,
  b5: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_scm_HashSet$$anon$1(outer) {
  this.eV = 0;
  this.eg = null;
  this.hf = 0;
  this.he = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $ct_scm_HashSet$HashSetIterator__scm_HashSet__(this, outer);
}
$p = $c_scm_HashSet$$anon$1.prototype = new $h_scm_HashSet$HashSetIterator();
$p.constructor = $c_scm_HashSet$$anon$1;
/** @constructor */
function $h_scm_HashSet$$anon$1() {
}
$h_scm_HashSet$$anon$1.prototype = $p;
$p.kD = (function(nd) {
  return nd.gk;
});
var $d_scm_HashSet$$anon$1 = new $TypeData().i($c_scm_HashSet$$anon$1, "scala.collection.mutable.HashSet$$anon$1", ({
  h3: 1,
  b6: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_scm_HashSet$$anon$2(outer) {
  this.eV = 0;
  this.eg = null;
  this.hf = 0;
  this.he = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  $ct_scm_HashSet$HashSetIterator__scm_HashSet__(this, outer);
}
$p = $c_scm_HashSet$$anon$2.prototype = new $h_scm_HashSet$HashSetIterator();
$p.constructor = $c_scm_HashSet$$anon$2;
/** @constructor */
function $h_scm_HashSet$$anon$2() {
}
$h_scm_HashSet$$anon$2.prototype = $p;
$p.kD = (function(nd) {
  return nd;
});
var $d_scm_HashSet$$anon$2 = new $TypeData().i($c_scm_HashSet$$anon$2, "scala.collection.mutable.HashSet$$anon$2", ({
  h4: 1,
  b6: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_scm_HashSet$$anon$3(outer) {
  this.eV = 0;
  this.eg = null;
  this.hf = 0;
  this.he = null;
  this.k5 = 0;
  this.nd = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.nd = outer;
  $ct_scm_HashSet$HashSetIterator__scm_HashSet__(this, outer);
  this.k5 = 0;
}
$p = $c_scm_HashSet$$anon$3.prototype = new $h_scm_HashSet$HashSetIterator();
$p.constructor = $c_scm_HashSet$$anon$3;
/** @constructor */
function $h_scm_HashSet$$anon$3() {
}
$h_scm_HashSet$$anon$3.prototype = $p;
$p.u = (function() {
  return this.k5;
});
$p.kD = (function(nd) {
  this.k5 = $p_scm_HashSet__improveHash__I__I(this.nd, nd.eh);
  return this;
});
var $d_scm_HashSet$$anon$3 = new $TypeData().i($c_scm_HashSet$$anon$3, "scala.collection.mutable.HashSet$$anon$3", ({
  h5: 1,
  b6: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1
}));
/** @constructor */
function $c_s_concurrent_duration_Duration$$anon$1() {
}
$p = $c_s_concurrent_duration_Duration$$anon$1.prototype = new $h_s_concurrent_duration_Duration$Infinite();
$p.constructor = $c_s_concurrent_duration_Duration$$anon$1;
/** @constructor */
function $h_s_concurrent_duration_Duration$$anon$1() {
}
$h_s_concurrent_duration_Duration$$anon$1.prototype = $p;
$p.t = (function() {
  return "Duration.Undefined";
});
$p.p = (function(other) {
  return false;
});
$p.kr = (function(other) {
  return ((other !== this) | 0);
});
var $d_s_concurrent_duration_Duration$$anon$1 = new $TypeData().i($c_s_concurrent_duration_Duration$$anon$1, "scala.concurrent.duration.Duration$$anon$1", ({
  hx: 1,
  hy: 1,
  cN: 1,
  a: 1,
  F: 1,
  bb: 1
}));
/** @constructor */
function $c_s_math_Ordering$$anon$1(f$4, outer) {
  this.kf = null;
  this.nq = null;
  this.kf = f$4;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.nq = outer;
}
$p = $c_s_math_Ordering$$anon$1.prototype = new $h_O();
$p.constructor = $c_s_math_Ordering$$anon$1;
/** @constructor */
function $h_s_math_Ordering$$anon$1() {
}
$h_s_math_Ordering$$anon$1.prototype = $p;
$p.dS = (function(x, y) {
  return $f_s_math_Ordering__gt__O__O__Z(this, x, y);
});
$p.dV = (function(other) {
  return $f_s_math_Ordering__isReverseOf__s_math_Ordering__Z(this, other);
});
$p.ax = (function(x, y) {
  return this.nq.ax(this.kf.d(x), this.kf.d(y));
});
var $d_s_math_Ordering$$anon$1 = new $TypeData().i($c_s_math_Ordering$$anon$1, "scala.math.Ordering$$anon$1", ({
  hG: 1,
  P: 1,
  a: 1,
  ab: 1,
  ad: 1,
  ac: 1
}));
function $f_s_math_Ordering$CachedReverse__isReverseOf__s_math_Ordering__Z($thiz, other) {
  return (other === $thiz.nr);
}
/** @constructor */
function $c_s_math_Ordering$Reverse(outer) {
  this.eY = null;
  this.eY = outer;
}
$p = $c_s_math_Ordering$Reverse.prototype = new $h_O();
$p.constructor = $c_s_math_Ordering$Reverse;
/** @constructor */
function $h_s_math_Ordering$Reverse() {
}
$h_s_math_Ordering$Reverse.prototype = $p;
$p.dV = (function(other) {
  var x$2 = this.eY;
  return ((other === null) ? (x$2 === null) : other.p(x$2));
});
$p.ax = (function(x, y) {
  return this.eY.ax(y, x);
});
$p.dS = (function(x, y) {
  return this.eY.dS(y, x);
});
$p.p = (function(obj) {
  if ((obj !== null)) {
    if ((this === obj)) {
      return true;
    }
  }
  if ((obj instanceof $c_s_math_Ordering$Reverse)) {
    var x = this.eY;
    var x$2 = obj.eY;
    return ((x === null) ? (x$2 === null) : x.p(x$2));
  }
  return false;
});
$p.u = (function() {
  return Math.imul(41, this.eY.u());
});
function $isArrayOf_s_math_Ordering$Reverse(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cV)));
}
var $d_s_math_Ordering$Reverse = new $TypeData().i($c_s_math_Ordering$Reverse, "scala.math.Ordering$Reverse", ({
  cV: 1,
  P: 1,
  a: 1,
  ab: 1,
  ad: 1,
  ac: 1
}));
/** @constructor */
function $c_s_reflect_ClassTag$GenericClassTag(runtimeClass) {
  this.ho = null;
  this.ho = runtimeClass;
}
$p = $c_s_reflect_ClassTag$GenericClassTag.prototype = new $h_O();
$p.constructor = $c_s_reflect_ClassTag$GenericClassTag;
/** @constructor */
function $h_s_reflect_ClassTag$GenericClassTag() {
}
$h_s_reflect_ClassTag$GenericClassTag.prototype = $p;
$p.p = (function(x) {
  return $f_s_reflect_ClassTag__equals__O__Z(this, x);
});
$p.u = (function() {
  return $m_sr_Statics$().y(this.ho);
});
$p.t = (function() {
  return $p_s_reflect_ClassTag__prettyprint$1__jl_Class__T(this, this.ho);
});
$p.bk = (function() {
  return this.ho;
});
$p.bU = (function(len) {
  return this.ho.J.U(len);
});
var $d_s_reflect_ClassTag$GenericClassTag = new $TypeData().i($c_s_reflect_ClassTag$GenericClassTag, "scala.reflect.ClassTag$GenericClassTag", ({
  hZ: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1
}));
function $ct_Ljava_io_PrintStream__Ljava_io_OutputStream__Z__Ljava_nio_charset_Charset__($thiz, _out, autoFlush, charset) {
  $ct_Ljava_io_FilterOutputStream__Ljava_io_OutputStream__($thiz, _out);
  return $thiz;
}
/** @constructor */
function $c_Ljava_io_PrintStream() {
}
$p = $c_Ljava_io_PrintStream.prototype = new $h_Ljava_io_FilterOutputStream();
$p.constructor = $c_Ljava_io_PrintStream;
/** @constructor */
function $h_Ljava_io_PrintStream() {
}
$h_Ljava_io_PrintStream.prototype = $p;
$p.fI = (function(s) {
  this.oE(s);
  this.kV("\n");
});
$p.pX = (function(csq) {
  this.oE(((csq === null) ? "null" : $dp_toString__T(csq)));
  return this;
});
$p.nN = (function(csq) {
  return this.pX(csq);
});
class $c_ju_DuplicateFormatFlagsException extends $c_ju_IllegalFormatException {
  constructor(f) {
    super();
    this.lN = null;
    this.lN = f;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return (("Flags = '" + this.lN) + "'");
  }
}
var $d_ju_DuplicateFormatFlagsException = new $TypeData().i($c_ju_DuplicateFormatFlagsException, "java.util.DuplicateFormatFlagsException", ({
  dW: 1,
  a1: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_FormatFlagsConversionMismatchException extends $c_ju_IllegalFormatException {
  constructor(f, c) {
    super();
    this.lP = null;
    this.lO = 0;
    this.lP = f;
    this.lO = c;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return ((("Conversion = " + $cToS(this.lO)) + ", Flags = ") + this.lP);
  }
}
var $d_ju_FormatFlagsConversionMismatchException = new $TypeData().i($c_ju_FormatFlagsConversionMismatchException, "java.util.FormatFlagsConversionMismatchException", ({
  dX: 1,
  a1: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_IllegalFormatArgumentIndexException extends $c_ju_IllegalFormatException {
  constructor(msg) {
    super();
    this.lT = null;
    this.lT = msg;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return this.lT;
  }
}
var $d_ju_IllegalFormatArgumentIndexException = new $TypeData().i($c_ju_IllegalFormatArgumentIndexException, "java.util.IllegalFormatArgumentIndexException", ({
  e4: 1,
  a1: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_IllegalFormatCodePointException extends $c_ju_IllegalFormatException {
  constructor(c) {
    super();
    this.lU = 0;
    this.lU = c;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    var i = this.lU;
    return ("Code point = 0x" + (i >>> 0.0).toString(16));
  }
}
var $d_ju_IllegalFormatCodePointException = new $TypeData().i($c_ju_IllegalFormatCodePointException, "java.util.IllegalFormatCodePointException", ({
  e5: 1,
  a1: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_IllegalFormatConversionException extends $c_ju_IllegalFormatException {
  constructor(c, arg) {
    super();
    this.lW = 0;
    this.lV = null;
    this.lW = c;
    this.lV = arg;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return ((("" + $cToS(this.lW)) + " != ") + this.lV.J.N);
  }
}
var $d_ju_IllegalFormatConversionException = new $TypeData().i($c_ju_IllegalFormatConversionException, "java.util.IllegalFormatConversionException", ({
  e6: 1,
  a1: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_IllegalFormatFlagsException extends $c_ju_IllegalFormatException {
  constructor(f) {
    super();
    this.lX = null;
    this.lX = f;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return (("Flags = '" + this.lX) + "'");
  }
}
var $d_ju_IllegalFormatFlagsException = new $TypeData().i($c_ju_IllegalFormatFlagsException, "java.util.IllegalFormatFlagsException", ({
  e7: 1,
  a1: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_IllegalFormatPrecisionException extends $c_ju_IllegalFormatException {
  constructor(p) {
    super();
    this.lY = 0;
    this.lY = p;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return ("" + this.lY);
  }
}
var $d_ju_IllegalFormatPrecisionException = new $TypeData().i($c_ju_IllegalFormatPrecisionException, "java.util.IllegalFormatPrecisionException", ({
  e8: 1,
  a1: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_IllegalFormatWidthException extends $c_ju_IllegalFormatException {
  constructor(w) {
    super();
    this.lZ = 0;
    this.lZ = w;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return ("" + this.lZ);
  }
}
var $d_ju_IllegalFormatWidthException = new $TypeData().i($c_ju_IllegalFormatWidthException, "java.util.IllegalFormatWidthException", ({
  e9: 1,
  a1: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_MissingFormatArgumentException extends $c_ju_IllegalFormatException {
  constructor(s) {
    super();
    this.m0 = null;
    this.m0 = s;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return (("Format specifier '" + this.m0) + "'");
  }
}
var $d_ju_MissingFormatArgumentException = new $TypeData().i($c_ju_MissingFormatArgumentException, "java.util.MissingFormatArgumentException", ({
  ea: 1,
  a1: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_MissingFormatWidthException extends $c_ju_IllegalFormatException {
  constructor(s) {
    super();
    this.m1 = null;
    this.m1 = s;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return this.m1;
  }
}
var $d_ju_MissingFormatWidthException = new $TypeData().i($c_ju_MissingFormatWidthException, "java.util.MissingFormatWidthException", ({
  eb: 1,
  a1: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
class $c_ju_UnknownFormatConversionException extends $c_ju_IllegalFormatException {
  constructor(s) {
    super();
    this.m2 = null;
    this.m2 = s;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return (("Conversion = '" + this.m2) + "'");
  }
}
var $d_ju_UnknownFormatConversionException = new $TypeData().i($c_ju_UnknownFormatConversionException, "java.util.UnknownFormatConversionException", ({
  ec: 1,
  a1: 1,
  Q: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1
}));
/** @constructor */
function $c_T2$mcII$sp(_1$mcI$sp, _2$mcI$sp) {
  this.jB = null;
  this.jC = null;
  this.jD = 0;
  this.jE = 0;
  this.jD = _1$mcI$sp;
  this.jE = _2$mcI$sp;
  $ct_T2__O__O__(this, null, null);
}
$p = $c_T2$mcII$sp.prototype = new $h_T2();
$p.constructor = $c_T2$mcII$sp;
/** @constructor */
function $h_T2$mcII$sp() {
}
$h_T2$mcII$sp.prototype = $p;
$p.nE = (function() {
  return this.jD;
});
$p.kk = (function() {
  return this.jE;
});
$p.ap = (function() {
  return this.jE;
});
$p.aw = (function() {
  return this.jD;
});
var $d_T2$mcII$sp = new $TypeData().i($c_T2$mcII$sp, "scala.Tuple2$mcII$sp", ({
  eY: 1,
  aW: 1,
  bE: 1,
  J: 1,
  d: 1,
  a: 1,
  eU: 1
}));
function $f_sc_View__toString__T($thiz) {
  return ($thiz.bK() + "(<not computed>)");
}
function $is_sc_View(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.C)));
}
function $isArrayOf_sc_View(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.C)));
}
/** @constructor */
function $c_sci_Set$Set2$$anon$1(outer) {
  this.ed = 0;
  this.dH = 0;
  this.mY = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.mY = outer;
  $ct_sci_Set$SetNIterator__I__(this, 2);
}
$p = $c_sci_Set$Set2$$anon$1.prototype = new $h_sci_Set$SetNIterator();
$p.constructor = $c_sci_Set$Set2$$anon$1;
/** @constructor */
function $h_sci_Set$Set2$$anon$1() {
}
$h_sci_Set$Set2$$anon$1.prototype = $p;
$p.r = (function(i) {
  return this.mY.rV(i);
});
var $d_sci_Set$Set2$$anon$1 = new $TypeData().i($c_sci_Set$Set2$$anon$1, "scala.collection.immutable.Set$Set2$$anon$1", ({
  gl: 1,
  b2: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1,
  a: 1
}));
/** @constructor */
function $c_sci_Set$Set3$$anon$2(outer) {
  this.ed = 0;
  this.dH = 0;
  this.mZ = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.mZ = outer;
  $ct_sci_Set$SetNIterator__I__(this, 3);
}
$p = $c_sci_Set$Set3$$anon$2.prototype = new $h_sci_Set$SetNIterator();
$p.constructor = $c_sci_Set$Set3$$anon$2;
/** @constructor */
function $h_sci_Set$Set3$$anon$2() {
}
$h_sci_Set$Set3$$anon$2.prototype = $p;
$p.r = (function(i) {
  return this.mZ.rW(i);
});
var $d_sci_Set$Set3$$anon$2 = new $TypeData().i($c_sci_Set$Set3$$anon$2, "scala.collection.immutable.Set$Set3$$anon$2", ({
  gm: 1,
  b2: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1,
  a: 1
}));
/** @constructor */
function $c_sci_Set$Set4$$anon$3(outer) {
  this.ed = 0;
  this.dH = 0;
  this.n0 = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.n0 = outer;
  $ct_sci_Set$SetNIterator__I__(this, 4);
}
$p = $c_sci_Set$Set4$$anon$3.prototype = new $h_sci_Set$SetNIterator();
$p.constructor = $c_sci_Set$Set4$$anon$3;
/** @constructor */
function $h_sci_Set$Set4$$anon$3() {
}
$h_sci_Set$Set4$$anon$3.prototype = $p;
$p.r = (function(i) {
  return this.n0.rX(i);
});
var $d_sci_Set$Set4$$anon$3 = new $TypeData().i($c_sci_Set$Set4$$anon$3, "scala.collection.immutable.Set$Set4$$anon$3", ({
  gn: 1,
  b2: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1,
  a: 1
}));
/** @constructor */
function $c_scm_ArrayBuilder$generic(elementClass) {
  this.jY = 0;
  this.n5 = 0;
  this.gj = null;
  this.n6 = false;
  this.jZ = null;
  this.gj = elementClass;
  $ct_scm_ArrayBuilder__(this);
  this.n6 = (elementClass === $d_C.l());
  this.jZ = [];
}
$p = $c_scm_ArrayBuilder$generic.prototype = new $h_scm_ArrayBuilder();
$p.constructor = $c_scm_ArrayBuilder$generic;
/** @constructor */
function $h_scm_ArrayBuilder$generic() {
}
$h_scm_ArrayBuilder$generic.prototype = $p;
$p.nL = (function(elem) {
  var unboxedElem = (this.n6 ? $uC(elem) : ((elem === null) ? $m_scm_ArrayBuilder$().j7(this.gj) : elem));
  this.jZ.push(unboxedElem);
  return this;
});
$p.pE = (function(xs) {
  var it = xs.c();
  while (it.k()) {
    this.nL(it.f());
  }
  return this;
});
$p.rO = (function(size) {
});
$p.aU = (function() {
  return $m_scm_ArrayBuilder$().j6(((this.gj === $d_V.l()) ? $d_jl_Void.l() : (((this.gj === $d_sr_Null$.l()) || (this.gj === $d_sr_Nothing$.l())) ? $d_O.l() : this.gj)), this.jZ);
});
$p.t = (function() {
  return "ArrayBuilder.generic";
});
$p.aq = (function(elem) {
  return this.nL(elem);
});
$p.b7 = (function(elems) {
  return this.pE(elems);
});
var $d_scm_ArrayBuilder$generic = new $TypeData().i($c_scm_ArrayBuilder$generic, "scala.collection.mutable.ArrayBuilder$generic", ({
  gO: 1,
  gM: 1,
  H: 1,
  I: 1,
  K: 1,
  a7: 1,
  a: 1
}));
/** @constructor */
function $c_scm_CheckedIndexedSeqView$CheckedIterator(self, mutationCount) {
  this.jF = null;
  this.e3 = 0;
  this.br = 0;
  this.na = null;
  this.n9 = 0;
  this.na = mutationCount;
  $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(this, self);
  this.n9 = (mutationCount.a2() | 0);
}
$p = $c_scm_CheckedIndexedSeqView$CheckedIterator.prototype = new $h_sc_IndexedSeqView$IndexedSeqViewIterator();
$p.constructor = $c_scm_CheckedIndexedSeqView$CheckedIterator;
/** @constructor */
function $h_scm_CheckedIndexedSeqView$CheckedIterator() {
}
$h_scm_CheckedIndexedSeqView$CheckedIterator.prototype = $p;
$p.k = (function() {
  $m_scm_MutationTracker$().kp(this.n9, (this.na.a2() | 0), "mutation occurred during iteration");
  return (this.br > 0);
});
var $d_scm_CheckedIndexedSeqView$CheckedIterator = new $TypeData().i($c_scm_CheckedIndexedSeqView$CheckedIterator, "scala.collection.mutable.CheckedIndexedSeqView$CheckedIterator", ({
  gT: 1,
  bM: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1,
  a: 1
}));
/** @constructor */
function $c_scm_CheckedIndexedSeqView$CheckedReverseIterator(self, mutationCount) {
  this.jG = null;
  this.bJ = 0;
  this.fd = 0;
  this.nc = null;
  this.nb = 0;
  this.nc = mutationCount;
  $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(this, self);
  this.nb = (mutationCount.a2() | 0);
}
$p = $c_scm_CheckedIndexedSeqView$CheckedReverseIterator.prototype = new $h_sc_IndexedSeqView$IndexedSeqViewReverseIterator();
$p.constructor = $c_scm_CheckedIndexedSeqView$CheckedReverseIterator;
/** @constructor */
function $h_scm_CheckedIndexedSeqView$CheckedReverseIterator() {
}
$h_scm_CheckedIndexedSeqView$CheckedReverseIterator.prototype = $p;
$p.k = (function() {
  $m_scm_MutationTracker$().kp(this.nb, (this.nc.a2() | 0), "mutation occurred during iteration");
  return (this.bJ > 0);
});
var $d_scm_CheckedIndexedSeqView$CheckedReverseIterator = new $TypeData().i($c_scm_CheckedIndexedSeqView$CheckedReverseIterator, "scala.collection.mutable.CheckedIndexedSeqView$CheckedReverseIterator", ({
  gU: 1,
  bN: 1,
  p: 1,
  b: 1,
  c: 1,
  m: 1,
  a: 1
}));
class $c_s_concurrent_Future$$anon$1 extends $c_ju_NoSuchElementException {
  constructor(t$2) {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, ("Future.collect partial function is not defined at: " + t$2), null, true, true);
  }
  gq() {
    return $f_s_util_control_NoStackTrace__fillInStackTrace__jl_Throwable(this);
  }
}
var $d_s_concurrent_Future$$anon$1 = new $TypeData().i($c_s_concurrent_Future$$anon$1, "scala.concurrent.Future$$anon$1", ({
  hr: 1,
  aH: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1,
  aM: 1
}));
class $c_s_concurrent_Future$$anon$2 extends $c_ju_NoSuchElementException {
  constructor() {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, "Future.filter predicate is not satisfied", null, true, true);
  }
  gq() {
    return $f_s_util_control_NoStackTrace__fillInStackTrace__jl_Throwable(this);
  }
}
var $d_s_concurrent_Future$$anon$2 = new $TypeData().i($c_s_concurrent_Future$$anon$2, "scala.concurrent.Future$$anon$2", ({
  hs: 1,
  aH: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1,
  aM: 1
}));
class $c_s_concurrent_Future$$anon$3 extends $c_ju_NoSuchElementException {
  constructor() {
    super();
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, "Future.failed not completed with a throwable.", null, true, true);
  }
  gq() {
    return $f_s_util_control_NoStackTrace__fillInStackTrace__jl_Throwable(this);
  }
}
var $d_s_concurrent_Future$$anon$3 = new $TypeData().i($c_s_concurrent_Future$$anon$3, "scala.concurrent.Future$$anon$3", ({
  ht: 1,
  aH: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1,
  aM: 1
}));
function $ct_s_concurrent_impl_Promise$DefaultPromise__O__($thiz, initial) {
  $ct_ju_concurrent_atomic_AtomicReference__O__($thiz, initial);
  return $thiz;
}
function $ct_s_concurrent_impl_Promise$DefaultPromise__s_util_Try__($thiz, result) {
  $ct_s_concurrent_impl_Promise$DefaultPromise__O__($thiz, $m_s_concurrent_impl_Promise$().hS(result));
  return $thiz;
}
function $ct_s_concurrent_impl_Promise$DefaultPromise__($thiz) {
  $ct_s_concurrent_impl_Promise$DefaultPromise__O__($thiz, $m_s_concurrent_impl_Promise$().hk);
  return $thiz;
}
function $p_s_concurrent_impl_Promise$DefaultPromise__value0__s_util_Try($thiz) {
  var \u03b4this$tailLocal2 = $thiz;
  while (true) {
    var state = \u03b4this$tailLocal2.ab;
    if ((state instanceof $c_s_util_Try)) {
      return state;
    } else if ((state instanceof $c_s_concurrent_impl_Promise$Link)) {
      \u03b4this$tailLocal2 = state.gx(\u03b4this$tailLocal2);
    } else {
      return null;
    }
  }
}
function $p_s_concurrent_impl_Promise$DefaultPromise__dispatchOrAddCallbacks__O__s_concurrent_impl_Promise$Callbacks__s_concurrent_impl_Promise$Callbacks($thiz, state, callbacks) {
  var \u03b4this$tailLocal4 = $thiz;
  var state$tailLocal2 = state;
  while (true) {
    if ((state$tailLocal2 instanceof $c_s_util_Try)) {
      $p_s_concurrent_impl_Promise$DefaultPromise__submitWithValue__s_concurrent_impl_Promise$Callbacks__s_util_Try__V(\u03b4this$tailLocal4, callbacks, state$tailLocal2);
      return callbacks;
    } else if ($is_s_concurrent_impl_Promise$Callbacks(state$tailLocal2)) {
      if (\u03b4this$tailLocal4.hD(state$tailLocal2, ((state$tailLocal2 !== $m_s_concurrent_impl_Promise$().hk) ? $p_s_concurrent_impl_Promise$DefaultPromise__concatCallbacks__s_concurrent_impl_Promise$Callbacks__s_concurrent_impl_Promise$Callbacks__s_concurrent_impl_Promise$Callbacks(\u03b4this$tailLocal4, callbacks, state$tailLocal2) : callbacks))) {
        return callbacks;
      } else {
        state$tailLocal2 = \u03b4this$tailLocal4.ab;
      }
    } else {
      var p = state$tailLocal2.gx(\u03b4this$tailLocal4);
      var state$tailLocal2$tmp1 = p.ab;
      \u03b4this$tailLocal4 = p;
      state$tailLocal2 = state$tailLocal2$tmp1;
    }
  }
}
function $p_s_concurrent_impl_Promise$DefaultPromise__concatCallbacks__s_concurrent_impl_Promise$Callbacks__s_concurrent_impl_Promise$Callbacks__s_concurrent_impl_Promise$Callbacks($thiz, left, right) {
  var right$tailLocal1 = right;
  var left$tailLocal1 = left;
  while (true) {
    if ((left$tailLocal1 instanceof $c_s_concurrent_impl_Promise$Transformation)) {
      return new $c_s_concurrent_impl_Promise$ManyCallbacks(left$tailLocal1, right$tailLocal1);
    } else {
      var m = left$tailLocal1;
      var left$tailLocal1$tmp1 = m.ke;
      var right$tailLocal1$tmp1 = new $c_s_concurrent_impl_Promise$ManyCallbacks(m.kd, right$tailLocal1);
      left$tailLocal1 = left$tailLocal1$tmp1;
      right$tailLocal1 = right$tailLocal1$tmp1;
    }
  }
}
function $p_s_concurrent_impl_Promise$DefaultPromise__submitWithValue__s_concurrent_impl_Promise$Callbacks__s_util_Try__V($thiz, callbacks, resolved) {
  var callbacks$tailLocal1 = callbacks;
  while (true) {
    if ((callbacks$tailLocal1 instanceof $c_s_concurrent_impl_Promise$ManyCallbacks)) {
      var m = callbacks$tailLocal1;
      m.kd.oX(resolved);
      callbacks$tailLocal1 = m.ke;
    } else {
      callbacks$tailLocal1.oX(resolved);
      return (void 0);
    }
  }
}
/** @constructor */
function $c_s_concurrent_impl_Promise$DefaultPromise() {
  this.ab = null;
}
$p = $c_s_concurrent_impl_Promise$DefaultPromise.prototype = new $h_ju_concurrent_atomic_AtomicReference();
$p.constructor = $c_s_concurrent_impl_Promise$DefaultPromise;
/** @constructor */
function $h_s_concurrent_impl_Promise$DefaultPromise() {
}
$h_s_concurrent_impl_Promise$DefaultPromise.prototype = $p;
$p.qN = (function(f, executor) {
  var state = this.ab;
  return ((!(state instanceof $c_s_util_Failure)) ? $p_s_concurrent_impl_Promise$DefaultPromise__dispatchOrAddCallbacks__O__s_concurrent_impl_Promise$Callbacks__s_concurrent_impl_Promise$Callbacks(this, state, $ct_s_concurrent_impl_Promise$Transformation__I__F1__s_concurrent_ExecutionContext__(new $c_s_concurrent_impl_Promise$Transformation(), 2, f, executor)) : this);
});
$p.oz = (function(f, executor) {
  var state = this.ab;
  return ((!(state instanceof $c_s_util_Failure)) ? $p_s_concurrent_impl_Promise$DefaultPromise__dispatchOrAddCallbacks__O__s_concurrent_impl_Promise$Callbacks__s_concurrent_impl_Promise$Callbacks(this, state, $ct_s_concurrent_impl_Promise$Transformation__I__F1__s_concurrent_ExecutionContext__(new $c_s_concurrent_impl_Promise$Transformation(), 1, f, executor)) : this);
});
$p.oC = (function(func, executor) {
  $p_s_concurrent_impl_Promise$DefaultPromise__dispatchOrAddCallbacks__O__s_concurrent_impl_Promise$Callbacks__s_concurrent_impl_Promise$Callbacks(this, this.ab, $ct_s_concurrent_impl_Promise$Transformation__I__F1__s_concurrent_ExecutionContext__(new $c_s_concurrent_impl_Promise$Transformation(), 6, func, executor));
});
$p.t = (function() {
  var \u03b4this$tailLocal1 = this;
  while (true) {
    var state = \u03b4this$tailLocal1.ab;
    if ((state instanceof $c_s_util_Try)) {
      return (("Future(" + state) + ")");
    } else if ((state instanceof $c_s_concurrent_impl_Promise$Link)) {
      \u03b4this$tailLocal1 = state.gx(\u03b4this$tailLocal1);
    } else {
      return "Future(<not completed>)";
    }
  }
});
$p.sm = (function(value) {
  var state = this.ab;
  return ((!(state instanceof $c_s_util_Try)) && this.fL(state, $m_s_concurrent_impl_Promise$().hS(value)));
});
$p.fL = (function(state, resolved) {
  var \u03b4this$tailLocal3 = this;
  var state$tailLocal1 = state;
  while (true) {
    if ($is_s_concurrent_impl_Promise$Callbacks(state$tailLocal1)) {
      if ((!\u03b4this$tailLocal3.hD(state$tailLocal1, resolved))) {
        state$tailLocal1 = \u03b4this$tailLocal3.ab;
        continue;
      }
      if ((state$tailLocal1 !== $m_s_concurrent_impl_Promise$().hk)) {
        $p_s_concurrent_impl_Promise$DefaultPromise__submitWithValue__s_concurrent_impl_Promise$Callbacks__s_util_Try__V(\u03b4this$tailLocal3, state$tailLocal1, resolved);
      }
      return true;
    } else if ((state$tailLocal1 instanceof $c_s_concurrent_impl_Promise$Link)) {
      var p = state$tailLocal1.gx(\u03b4this$tailLocal3);
      if ((p !== \u03b4this$tailLocal3)) {
        var state$tailLocal1$tmp1 = p.ab;
        \u03b4this$tailLocal3 = p;
        state$tailLocal1 = state$tailLocal1$tmp1;
        continue;
      }
      return false;
    } else {
      return false;
    }
  }
});
$p.kt = (function(other) {
  if ((other !== this)) {
    var state = this.ab;
    if ((!(state instanceof $c_s_util_Try))) {
      if ((other instanceof $c_s_concurrent_impl_Promise$DefaultPromise)) {
        var resolved = $p_s_concurrent_impl_Promise$DefaultPromise__value0__s_util_Try(other);
      } else {
        var this$1 = $m_s_Option$().hB($p_s_concurrent_impl_Promise$DefaultPromise__value0__s_util_Try(other));
        var resolved = (this$1.g() ? null : this$1.S());
      }
      if ((resolved !== null)) {
        this.fL(state, resolved);
      } else {
        other.oC(this, $m_s_concurrent_ExecutionContext$parasitic$());
      }
    }
  }
  return this;
});
$p.kZ = (function(target, link) {
  var \u03b4this$tailLocal5 = this;
  var link$tailLocal1 = link;
  var target$tailLocal2 = target;
  while (true) {
    if ((\u03b4this$tailLocal5 !== target$tailLocal2)) {
      var state = \u03b4this$tailLocal5.ab;
      if ((state instanceof $c_s_util_Try)) {
        if ((!target$tailLocal2.fL(target$tailLocal2.ab, state))) {
          throw $ct_jl_IllegalStateException__T__(new $c_jl_IllegalStateException(), "Cannot link completed promises together");
        } else {
          return (void 0);
        }
      } else if ($is_s_concurrent_impl_Promise$Callbacks(state)) {
        var l = ((link$tailLocal1 !== null) ? link$tailLocal1 : new $c_s_concurrent_impl_Promise$Link(target$tailLocal2));
        var p = l.gx(\u03b4this$tailLocal5);
        if (((\u03b4this$tailLocal5 !== p) && \u03b4this$tailLocal5.hD(state, l))) {
          if ((state !== $m_s_concurrent_impl_Promise$().hk)) {
            $p_s_concurrent_impl_Promise$DefaultPromise__dispatchOrAddCallbacks__O__s_concurrent_impl_Promise$Callbacks__s_concurrent_impl_Promise$Callbacks(p, p.ab, state);
            return (void 0);
          } else {
            return (void 0);
          }
        } else {
          target$tailLocal2 = p;
          link$tailLocal1 = l;
        }
      } else {
        \u03b4this$tailLocal5 = state.gx(\u03b4this$tailLocal5);
      }
    } else {
      return (void 0);
    }
  }
});
$p.so = (function(resolved) {
  var \u03b4this$tailLocal6 = this;
  while (true) {
    var state = \u03b4this$tailLocal6.ab;
    if ((state instanceof $c_s_concurrent_impl_Promise$Link)) {
      var next = (\u03b4this$tailLocal6.hD(state, resolved) ? state.ab : \u03b4this$tailLocal6);
      \u03b4this$tailLocal6 = next;
    } else {
      \u03b4this$tailLocal6.fL(state, resolved);
      return (void 0);
    }
  }
});
$p.d = (function(v1) {
  this.fL(this.ab, v1);
});
function $isArrayOf_s_concurrent_impl_Promise$DefaultPromise(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.ba)));
}
var $d_s_concurrent_impl_Promise$DefaultPromise = new $TypeData().i($c_s_concurrent_impl_Promise$DefaultPromise, "scala.concurrent.impl.Promise$DefaultPromise", ({
  ba: 1,
  aU: 1,
  a: 1,
  cM: 1,
  cJ: 1,
  cL: 1,
  i: 1
}));
/** @constructor */
function $c_s_math_Ordering$Boolean$() {
}
$p = $c_s_math_Ordering$Boolean$.prototype = new $h_O();
$p.constructor = $c_s_math_Ordering$Boolean$;
/** @constructor */
function $h_s_math_Ordering$Boolean$() {
}
$h_s_math_Ordering$Boolean$.prototype = $p;
$p.dS = (function(x, y) {
  return $f_s_math_Ordering__gt__O__O__Z(this, x, y);
});
$p.dV = (function(other) {
  return $f_s_math_Ordering__isReverseOf__s_math_Ordering__Z(this, other);
});
$p.ax = (function(x, y) {
  var x$1 = (!(!x));
  return ((x$1 === (!(!y))) ? 0 : (x$1 ? 1 : (-1)));
});
var $d_s_math_Ordering$Boolean$ = new $TypeData().i($c_s_math_Ordering$Boolean$, "scala.math.Ordering$Boolean$", ({
  hH: 1,
  P: 1,
  a: 1,
  ab: 1,
  ad: 1,
  ac: 1,
  hI: 1
}));
var $n_s_math_Ordering$Boolean$;
function $m_s_math_Ordering$Boolean$() {
  if ((!$n_s_math_Ordering$Boolean$)) {
    $n_s_math_Ordering$Boolean$ = new $c_s_math_Ordering$Boolean$();
  }
  return $n_s_math_Ordering$Boolean$;
}
/** @constructor */
function $c_s_math_Ordering$Byte$() {
}
$p = $c_s_math_Ordering$Byte$.prototype = new $h_O();
$p.constructor = $c_s_math_Ordering$Byte$;
/** @constructor */
function $h_s_math_Ordering$Byte$() {
}
$h_s_math_Ordering$Byte$.prototype = $p;
$p.dS = (function(x, y) {
  return $f_s_math_Ordering__gt__O__O__Z(this, x, y);
});
$p.dV = (function(other) {
  return $f_s_math_Ordering__isReverseOf__s_math_Ordering__Z(this, other);
});
$p.ax = (function(x, y) {
  return (((x | 0) - (y | 0)) | 0);
});
var $d_s_math_Ordering$Byte$ = new $TypeData().i($c_s_math_Ordering$Byte$, "scala.math.Ordering$Byte$", ({
  hJ: 1,
  P: 1,
  a: 1,
  ab: 1,
  ad: 1,
  ac: 1,
  hK: 1
}));
var $n_s_math_Ordering$Byte$;
function $m_s_math_Ordering$Byte$() {
  if ((!$n_s_math_Ordering$Byte$)) {
    $n_s_math_Ordering$Byte$ = new $c_s_math_Ordering$Byte$();
  }
  return $n_s_math_Ordering$Byte$;
}
/** @constructor */
function $c_s_math_Ordering$Char$() {
}
$p = $c_s_math_Ordering$Char$.prototype = new $h_O();
$p.constructor = $c_s_math_Ordering$Char$;
/** @constructor */
function $h_s_math_Ordering$Char$() {
}
$h_s_math_Ordering$Char$.prototype = $p;
$p.dS = (function(x, y) {
  return $f_s_math_Ordering__gt__O__O__Z(this, x, y);
});
$p.dV = (function(other) {
  return $f_s_math_Ordering__isReverseOf__s_math_Ordering__Z(this, other);
});
$p.ax = (function(x, y) {
  return (($uC(x) - $uC(y)) | 0);
});
var $d_s_math_Ordering$Char$ = new $TypeData().i($c_s_math_Ordering$Char$, "scala.math.Ordering$Char$", ({
  hM: 1,
  P: 1,
  a: 1,
  ab: 1,
  ad: 1,
  ac: 1,
  hN: 1
}));
var $n_s_math_Ordering$Char$;
function $m_s_math_Ordering$Char$() {
  if ((!$n_s_math_Ordering$Char$)) {
    $n_s_math_Ordering$Char$ = new $c_s_math_Ordering$Char$();
  }
  return $n_s_math_Ordering$Char$;
}
/** @constructor */
function $c_s_math_Ordering$DeprecatedDoubleOrdering$() {
}
$p = $c_s_math_Ordering$DeprecatedDoubleOrdering$.prototype = new $h_O();
$p.constructor = $c_s_math_Ordering$DeprecatedDoubleOrdering$;
/** @constructor */
function $h_s_math_Ordering$DeprecatedDoubleOrdering$() {
}
$h_s_math_Ordering$DeprecatedDoubleOrdering$.prototype = $p;
$p.dS = (function(x, y) {
  return $f_s_math_Ordering__gt__O__O__Z(this, x, y);
});
$p.dV = (function(other) {
  return $f_s_math_Ordering__isReverseOf__s_math_Ordering__Z(this, other);
});
$p.ax = (function(x, y) {
  return $m_jl_Double$().kq((+x), (+y));
});
var $d_s_math_Ordering$DeprecatedDoubleOrdering$ = new $TypeData().i($c_s_math_Ordering$DeprecatedDoubleOrdering$, "scala.math.Ordering$DeprecatedDoubleOrdering$", ({
  hO: 1,
  P: 1,
  a: 1,
  ab: 1,
  ad: 1,
  ac: 1,
  hP: 1
}));
var $n_s_math_Ordering$DeprecatedDoubleOrdering$;
function $m_s_math_Ordering$DeprecatedDoubleOrdering$() {
  if ((!$n_s_math_Ordering$DeprecatedDoubleOrdering$)) {
    $n_s_math_Ordering$DeprecatedDoubleOrdering$ = new $c_s_math_Ordering$DeprecatedDoubleOrdering$();
  }
  return $n_s_math_Ordering$DeprecatedDoubleOrdering$;
}
/** @constructor */
function $c_s_math_Ordering$Long$() {
}
$p = $c_s_math_Ordering$Long$.prototype = new $h_O();
$p.constructor = $c_s_math_Ordering$Long$;
/** @constructor */
function $h_s_math_Ordering$Long$() {
}
$h_s_math_Ordering$Long$.prototype = $p;
$p.dS = (function(x, y) {
  return $f_s_math_Ordering__gt__O__O__Z(this, x, y);
});
$p.dV = (function(other) {
  return $f_s_math_Ordering__isReverseOf__s_math_Ordering__Z(this, other);
});
$p.ax = (function(x, y) {
  var $x_1 = $uJ(x);
  var x$1_$_lo = $x_1.l;
  var x$1_$_hi = $x_1.h;
  var $x_2 = $uJ(y);
  var y$1_$_lo = $x_2.l;
  var y$1_$_hi = $x_2.h;
  return ((x$1_$_hi === y$1_$_hi) ? ((x$1_$_lo === y$1_$_lo) ? 0 : (((x$1_$_lo >>> 0) < (y$1_$_lo >>> 0)) ? (-1) : 1)) : ((x$1_$_hi < y$1_$_hi) ? (-1) : 1));
});
var $d_s_math_Ordering$Long$ = new $TypeData().i($c_s_math_Ordering$Long$, "scala.math.Ordering$Long$", ({
  hS: 1,
  P: 1,
  a: 1,
  ab: 1,
  ad: 1,
  ac: 1,
  hT: 1
}));
var $n_s_math_Ordering$Long$;
function $m_s_math_Ordering$Long$() {
  if ((!$n_s_math_Ordering$Long$)) {
    $n_s_math_Ordering$Long$ = new $c_s_math_Ordering$Long$();
  }
  return $n_s_math_Ordering$Long$;
}
/** @constructor */
function $c_s_math_Ordering$Short$() {
}
$p = $c_s_math_Ordering$Short$.prototype = new $h_O();
$p.constructor = $c_s_math_Ordering$Short$;
/** @constructor */
function $h_s_math_Ordering$Short$() {
}
$h_s_math_Ordering$Short$.prototype = $p;
$p.dS = (function(x, y) {
  return $f_s_math_Ordering__gt__O__O__Z(this, x, y);
});
$p.dV = (function(other) {
  return $f_s_math_Ordering__isReverseOf__s_math_Ordering__Z(this, other);
});
$p.ax = (function(x, y) {
  return (((x | 0) - (y | 0)) | 0);
});
var $d_s_math_Ordering$Short$ = new $TypeData().i($c_s_math_Ordering$Short$, "scala.math.Ordering$Short$", ({
  hU: 1,
  P: 1,
  a: 1,
  ab: 1,
  ad: 1,
  ac: 1,
  hV: 1
}));
var $n_s_math_Ordering$Short$;
function $m_s_math_Ordering$Short$() {
  if ((!$n_s_math_Ordering$Short$)) {
    $n_s_math_Ordering$Short$ = new $c_s_math_Ordering$Short$();
  }
  return $n_s_math_Ordering$Short$;
}
/** @constructor */
function $c_s_reflect_AnyValManifest() {
  this.av = null;
}
$p = $c_s_reflect_AnyValManifest.prototype = new $h_O();
$p.constructor = $c_s_reflect_AnyValManifest;
/** @constructor */
function $h_s_reflect_AnyValManifest() {
}
$h_s_reflect_AnyValManifest.prototype = $p;
$p.t = (function() {
  return this.av;
});
$p.p = (function(that) {
  return (this === that);
});
$p.u = (function() {
  return $systemIdentityHashCode(this);
});
/** @constructor */
function $c_s_reflect_ManifestFactory$ClassTypeManifest() {
}
$p = $c_s_reflect_ManifestFactory$ClassTypeManifest.prototype = new $h_O();
$p.constructor = $c_s_reflect_ManifestFactory$ClassTypeManifest;
/** @constructor */
function $h_s_reflect_ManifestFactory$ClassTypeManifest() {
}
$h_s_reflect_ManifestFactory$ClassTypeManifest.prototype = $p;
class $c_sjs_js_JavaScriptException extends $c_jl_RuntimeException {
  constructor(exception) {
    super();
    this.di = null;
    this.di = exception;
    $ct_jl_Throwable__T__jl_Throwable__Z__Z__(this, null, null, true, true);
  }
  c1() {
    return $dp_toString__T(this.di);
  }
  bG() {
    return "JavaScriptException";
  }
  bE() {
    return 1;
  }
  bF(x$1) {
    return ((x$1 === 0) ? this.di : $m_sr_Statics$().re(x$1));
  }
  cd() {
    return new $c_sr_ScalaRunTime$$anon$1(this);
  }
  u() {
    return $m_s_util_hashing_MurmurHash3$().dr(this, 1744042595, true);
  }
  p(x$1) {
    return ((this === x$1) || ((x$1 instanceof $c_sjs_js_JavaScriptException) && $m_sr_BoxesRunTime$().n(this.di, x$1.di)));
  }
}
function $isArrayOf_sjs_js_JavaScriptException(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cZ)));
}
var $d_sjs_js_JavaScriptException = new $TypeData().i($c_sjs_js_JavaScriptException, "scala.scalajs.js.JavaScriptException", ({
  cZ: 1,
  w: 1,
  v: 1,
  r: 1,
  a: 1,
  J: 1,
  d: 1
}));
function $p_jl_JSConsoleBasedPrintStream__doWriteLine__T__V($thiz, line) {
  if (((typeof console) !== "undefined")) {
    if (($thiz.lF && (!(!(!(!console.error)))))) {
      console.error(line);
    } else {
      console.log(line);
    }
  }
}
/** @constructor */
function $c_jl_JSConsoleBasedPrintStream(isErr) {
  this.lF = false;
  this.gI = null;
  this.lF = isErr;
  $ct_Ljava_io_PrintStream__Ljava_io_OutputStream__Z__Ljava_nio_charset_Charset__(this, new $c_jl_JSConsoleBasedPrintStream$DummyOutputStream(), false, null);
  this.gI = "";
}
$p = $c_jl_JSConsoleBasedPrintStream.prototype = new $h_Ljava_io_PrintStream();
$p.constructor = $c_jl_JSConsoleBasedPrintStream;
/** @constructor */
function $h_jl_JSConsoleBasedPrintStream() {
}
$h_jl_JSConsoleBasedPrintStream.prototype = $p;
$p.oE = (function(s) {
  this.kV(((s === null) ? "null" : s));
});
$p.kV = (function(s) {
  var rest = s;
  while ((rest !== "")) {
    var this$1 = rest;
    var nlPos = (this$1.indexOf("\n") | 0);
    if ((nlPos < 0)) {
      this.gI = (("" + this.gI) + rest);
      rest = "";
    } else {
      var $x_1 = this.gI;
      var this$2 = rest;
      $p_jl_JSConsoleBasedPrintStream__doWriteLine__T__V(this, (("" + $x_1) + this$2.substring(0, nlPos)));
      this.gI = "";
      var this$4 = rest;
      var beginIndex = ((1 + nlPos) | 0);
      rest = this$4.substring(beginIndex);
    }
  }
});
var $d_jl_JSConsoleBasedPrintStream = new $TypeData().i($c_jl_JSConsoleBasedPrintStream, "java.lang.JSConsoleBasedPrintStream", ({
  dx: 1,
  dl: 1,
  dj: 1,
  bm: 1,
  aN: 1,
  aP: 1,
  aO: 1,
  bn: 1
}));
function $p_sc_StrictOptimizedLinearSeqOps__loop$2__I__sc_LinearSeq__sc_LinearSeq($thiz, n, s) {
  var s$tailLocal1 = s;
  var n$tailLocal1 = n;
  while (true) {
    if (((n$tailLocal1 <= 0) || s$tailLocal1.g())) {
      return s$tailLocal1;
    } else {
      var n$tailLocal1$tmp1 = ((n$tailLocal1 - 1) | 0);
      var s$tailLocal1$tmp1 = s$tailLocal1.o();
      n$tailLocal1 = n$tailLocal1$tmp1;
      s$tailLocal1 = s$tailLocal1$tmp1;
    }
  }
}
function $f_sci_StrictOptimizedSeqOps__distinctBy__F1__O($thiz, f) {
  if (($thiz.aI(1) <= 0)) {
    return $thiz;
  } else {
    var builder = $thiz.cI();
    var seen = $ct_scm_HashSet__(new $c_scm_HashSet());
    var it = $thiz.c();
    var different = false;
    while (it.k()) {
      var next = it.f();
      if (seen.iN(f.d(next))) {
        builder.aq(next);
      } else {
        different = true;
      }
    }
    return (different ? builder.aU() : $thiz);
  }
}
/** @constructor */
function $c_s_math_Ordering$Int$() {
  this.nr = null;
  $n_s_math_Ordering$Int$ = this;
  this.nr = new $c_s_math_Ordering$Reverse(this);
}
$p = $c_s_math_Ordering$Int$.prototype = new $h_O();
$p.constructor = $c_s_math_Ordering$Int$;
/** @constructor */
function $h_s_math_Ordering$Int$() {
}
$h_s_math_Ordering$Int$.prototype = $p;
$p.dS = (function(x, y) {
  return $f_s_math_Ordering__gt__O__O__Z(this, x, y);
});
$p.dV = (function(other) {
  return $f_s_math_Ordering$CachedReverse__isReverseOf__s_math_Ordering__Z(this, other);
});
$p.ax = (function(x, y) {
  var x$1 = (x | 0);
  var y$1 = (y | 0);
  return ((x$1 === y$1) ? 0 : ((x$1 < y$1) ? (-1) : 1));
});
var $d_s_math_Ordering$Int$ = new $TypeData().i($c_s_math_Ordering$Int$, "scala.math.Ordering$Int$", ({
  hQ: 1,
  P: 1,
  a: 1,
  ab: 1,
  ad: 1,
  ac: 1,
  hR: 1,
  hL: 1
}));
var $n_s_math_Ordering$Int$;
function $m_s_math_Ordering$Int$() {
  if ((!$n_s_math_Ordering$Int$)) {
    $n_s_math_Ordering$Int$ = new $c_s_math_Ordering$Int$();
  }
  return $n_s_math_Ordering$Int$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$BooleanManifest() {
  this.av = null;
}
$p = $c_s_reflect_ManifestFactory$BooleanManifest.prototype = new $h_s_reflect_AnyValManifest();
$p.constructor = $c_s_reflect_ManifestFactory$BooleanManifest;
/** @constructor */
function $h_s_reflect_ManifestFactory$BooleanManifest() {
}
$h_s_reflect_ManifestFactory$BooleanManifest.prototype = $p;
$p.bk = (function() {
  return $d_Z.l();
});
$p.bU = (function(len) {
  return new $ac_Z(len);
});
/** @constructor */
function $c_s_reflect_ManifestFactory$ByteManifest() {
  this.av = null;
}
$p = $c_s_reflect_ManifestFactory$ByteManifest.prototype = new $h_s_reflect_AnyValManifest();
$p.constructor = $c_s_reflect_ManifestFactory$ByteManifest;
/** @constructor */
function $h_s_reflect_ManifestFactory$ByteManifest() {
}
$h_s_reflect_ManifestFactory$ByteManifest.prototype = $p;
$p.bk = (function() {
  return $d_B.l();
});
$p.bU = (function(len) {
  return new $ac_B(len);
});
/** @constructor */
function $c_s_reflect_ManifestFactory$CharManifest() {
  this.av = null;
}
$p = $c_s_reflect_ManifestFactory$CharManifest.prototype = new $h_s_reflect_AnyValManifest();
$p.constructor = $c_s_reflect_ManifestFactory$CharManifest;
/** @constructor */
function $h_s_reflect_ManifestFactory$CharManifest() {
}
$h_s_reflect_ManifestFactory$CharManifest.prototype = $p;
$p.bk = (function() {
  return $d_C.l();
});
$p.bU = (function(len) {
  return new $ac_C(len);
});
/** @constructor */
function $c_s_reflect_ManifestFactory$DoubleManifest() {
  this.av = null;
}
$p = $c_s_reflect_ManifestFactory$DoubleManifest.prototype = new $h_s_reflect_AnyValManifest();
$p.constructor = $c_s_reflect_ManifestFactory$DoubleManifest;
/** @constructor */
function $h_s_reflect_ManifestFactory$DoubleManifest() {
}
$h_s_reflect_ManifestFactory$DoubleManifest.prototype = $p;
$p.bk = (function() {
  return $d_D.l();
});
$p.bU = (function(len) {
  return new $ac_D(len);
});
/** @constructor */
function $c_s_reflect_ManifestFactory$FloatManifest() {
  this.av = null;
}
$p = $c_s_reflect_ManifestFactory$FloatManifest.prototype = new $h_s_reflect_AnyValManifest();
$p.constructor = $c_s_reflect_ManifestFactory$FloatManifest;
/** @constructor */
function $h_s_reflect_ManifestFactory$FloatManifest() {
}
$h_s_reflect_ManifestFactory$FloatManifest.prototype = $p;
$p.bk = (function() {
  return $d_F.l();
});
$p.bU = (function(len) {
  return new $ac_F(len);
});
/** @constructor */
function $c_s_reflect_ManifestFactory$IntManifest() {
  this.av = null;
}
$p = $c_s_reflect_ManifestFactory$IntManifest.prototype = new $h_s_reflect_AnyValManifest();
$p.constructor = $c_s_reflect_ManifestFactory$IntManifest;
/** @constructor */
function $h_s_reflect_ManifestFactory$IntManifest() {
}
$h_s_reflect_ManifestFactory$IntManifest.prototype = $p;
$p.bk = (function() {
  return $d_I.l();
});
$p.bU = (function(len) {
  return new $ac_I(len);
});
/** @constructor */
function $c_s_reflect_ManifestFactory$LongManifest() {
  this.av = null;
}
$p = $c_s_reflect_ManifestFactory$LongManifest.prototype = new $h_s_reflect_AnyValManifest();
$p.constructor = $c_s_reflect_ManifestFactory$LongManifest;
/** @constructor */
function $h_s_reflect_ManifestFactory$LongManifest() {
}
$h_s_reflect_ManifestFactory$LongManifest.prototype = $p;
$p.bk = (function() {
  return $d_J.l();
});
$p.bU = (function(len) {
  return new $ac_J(len);
});
/** @constructor */
function $c_s_reflect_ManifestFactory$PhantomManifest() {
  this.ej = null;
}
$p = $c_s_reflect_ManifestFactory$PhantomManifest.prototype = new $h_s_reflect_ManifestFactory$ClassTypeManifest();
$p.constructor = $c_s_reflect_ManifestFactory$PhantomManifest;
/** @constructor */
function $h_s_reflect_ManifestFactory$PhantomManifest() {
}
$h_s_reflect_ManifestFactory$PhantomManifest.prototype = $p;
$p.t = (function() {
  return this.ej;
});
$p.p = (function(that) {
  return (this === that);
});
$p.u = (function() {
  return $systemIdentityHashCode(this);
});
/** @constructor */
function $c_s_reflect_ManifestFactory$ShortManifest() {
  this.av = null;
}
$p = $c_s_reflect_ManifestFactory$ShortManifest.prototype = new $h_s_reflect_AnyValManifest();
$p.constructor = $c_s_reflect_ManifestFactory$ShortManifest;
/** @constructor */
function $h_s_reflect_ManifestFactory$ShortManifest() {
}
$h_s_reflect_ManifestFactory$ShortManifest.prototype = $p;
$p.bk = (function() {
  return $d_S.l();
});
$p.bU = (function(len) {
  return new $ac_S(len);
});
/** @constructor */
function $c_s_reflect_ManifestFactory$UnitManifest() {
  this.av = null;
}
$p = $c_s_reflect_ManifestFactory$UnitManifest.prototype = new $h_s_reflect_AnyValManifest();
$p.constructor = $c_s_reflect_ManifestFactory$UnitManifest;
/** @constructor */
function $h_s_reflect_ManifestFactory$UnitManifest() {
}
$h_s_reflect_ManifestFactory$UnitManifest.prototype = $p;
$p.bk = (function() {
  return $d_V.l();
});
$p.bU = (function(len) {
  return new ($d_jl_Void.r().C)(len);
});
/** @constructor */
function $c_sc_AbstractView() {
}
$p = $c_sc_AbstractView.prototype = new $h_sc_AbstractIterable();
$p.constructor = $c_sc_AbstractView;
/** @constructor */
function $h_sc_AbstractView() {
}
$h_sc_AbstractView.prototype = $p;
$p.aH = (function() {
  return $m_sc_View$();
});
$p.t = (function() {
  return $f_sc_View__toString__T(this);
});
$p.bl = (function() {
  return "View";
});
function $f_sc_Set__equals__O__Z($thiz, that) {
  if (($thiz === that)) {
    return true;
  } else if ($is_sc_Set(that)) {
    if (($thiz.M() === that.M())) {
      try {
        return $thiz.l9(that);
      } catch (e) {
        if (false) {
          return false;
        } else {
          throw e;
        }
      }
    } else {
      return false;
    }
  } else {
    return false;
  }
}
function $is_sc_Set(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.aa)));
}
function $isArrayOf_sc_Set(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.aa)));
}
/** @constructor */
function $c_s_reflect_ManifestFactory$AnyManifest$() {
  this.ej = null;
  this.ej = "Any";
  $m_sci_Nil$();
}
$p = $c_s_reflect_ManifestFactory$AnyManifest$.prototype = new $h_s_reflect_ManifestFactory$PhantomManifest();
$p.constructor = $c_s_reflect_ManifestFactory$AnyManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$AnyManifest$() {
}
$h_s_reflect_ManifestFactory$AnyManifest$.prototype = $p;
$p.bk = (function() {
  return $d_O.l();
});
$p.bU = (function(len) {
  return new $ac_O(len);
});
var $d_s_reflect_ManifestFactory$AnyManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$AnyManifest$, "scala.reflect.ManifestFactory$AnyManifest$", ({
  i0: 1,
  aL: 1,
  aK: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$AnyManifest$;
function $m_s_reflect_ManifestFactory$AnyManifest$() {
  if ((!$n_s_reflect_ManifestFactory$AnyManifest$)) {
    $n_s_reflect_ManifestFactory$AnyManifest$ = new $c_s_reflect_ManifestFactory$AnyManifest$();
  }
  return $n_s_reflect_ManifestFactory$AnyManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$BooleanManifest$() {
  this.av = null;
  this.av = "Boolean";
}
$p = $c_s_reflect_ManifestFactory$BooleanManifest$.prototype = new $h_s_reflect_ManifestFactory$BooleanManifest();
$p.constructor = $c_s_reflect_ManifestFactory$BooleanManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$BooleanManifest$() {
}
$h_s_reflect_ManifestFactory$BooleanManifest$.prototype = $p;
var $d_s_reflect_ManifestFactory$BooleanManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$BooleanManifest$, "scala.reflect.ManifestFactory$BooleanManifest$", ({
  i2: 1,
  i1: 1,
  ae: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$BooleanManifest$;
function $m_s_reflect_ManifestFactory$BooleanManifest$() {
  if ((!$n_s_reflect_ManifestFactory$BooleanManifest$)) {
    $n_s_reflect_ManifestFactory$BooleanManifest$ = new $c_s_reflect_ManifestFactory$BooleanManifest$();
  }
  return $n_s_reflect_ManifestFactory$BooleanManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$ByteManifest$() {
  this.av = null;
  this.av = "Byte";
}
$p = $c_s_reflect_ManifestFactory$ByteManifest$.prototype = new $h_s_reflect_ManifestFactory$ByteManifest();
$p.constructor = $c_s_reflect_ManifestFactory$ByteManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$ByteManifest$() {
}
$h_s_reflect_ManifestFactory$ByteManifest$.prototype = $p;
var $d_s_reflect_ManifestFactory$ByteManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$ByteManifest$, "scala.reflect.ManifestFactory$ByteManifest$", ({
  i4: 1,
  i3: 1,
  ae: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$ByteManifest$;
function $m_s_reflect_ManifestFactory$ByteManifest$() {
  if ((!$n_s_reflect_ManifestFactory$ByteManifest$)) {
    $n_s_reflect_ManifestFactory$ByteManifest$ = new $c_s_reflect_ManifestFactory$ByteManifest$();
  }
  return $n_s_reflect_ManifestFactory$ByteManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$CharManifest$() {
  this.av = null;
  this.av = "Char";
}
$p = $c_s_reflect_ManifestFactory$CharManifest$.prototype = new $h_s_reflect_ManifestFactory$CharManifest();
$p.constructor = $c_s_reflect_ManifestFactory$CharManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$CharManifest$() {
}
$h_s_reflect_ManifestFactory$CharManifest$.prototype = $p;
var $d_s_reflect_ManifestFactory$CharManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$CharManifest$, "scala.reflect.ManifestFactory$CharManifest$", ({
  i6: 1,
  i5: 1,
  ae: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$CharManifest$;
function $m_s_reflect_ManifestFactory$CharManifest$() {
  if ((!$n_s_reflect_ManifestFactory$CharManifest$)) {
    $n_s_reflect_ManifestFactory$CharManifest$ = new $c_s_reflect_ManifestFactory$CharManifest$();
  }
  return $n_s_reflect_ManifestFactory$CharManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$DoubleManifest$() {
  this.av = null;
  this.av = "Double";
}
$p = $c_s_reflect_ManifestFactory$DoubleManifest$.prototype = new $h_s_reflect_ManifestFactory$DoubleManifest();
$p.constructor = $c_s_reflect_ManifestFactory$DoubleManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$DoubleManifest$() {
}
$h_s_reflect_ManifestFactory$DoubleManifest$.prototype = $p;
var $d_s_reflect_ManifestFactory$DoubleManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$DoubleManifest$, "scala.reflect.ManifestFactory$DoubleManifest$", ({
  i8: 1,
  i7: 1,
  ae: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$DoubleManifest$;
function $m_s_reflect_ManifestFactory$DoubleManifest$() {
  if ((!$n_s_reflect_ManifestFactory$DoubleManifest$)) {
    $n_s_reflect_ManifestFactory$DoubleManifest$ = new $c_s_reflect_ManifestFactory$DoubleManifest$();
  }
  return $n_s_reflect_ManifestFactory$DoubleManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$FloatManifest$() {
  this.av = null;
  this.av = "Float";
}
$p = $c_s_reflect_ManifestFactory$FloatManifest$.prototype = new $h_s_reflect_ManifestFactory$FloatManifest();
$p.constructor = $c_s_reflect_ManifestFactory$FloatManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$FloatManifest$() {
}
$h_s_reflect_ManifestFactory$FloatManifest$.prototype = $p;
var $d_s_reflect_ManifestFactory$FloatManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$FloatManifest$, "scala.reflect.ManifestFactory$FloatManifest$", ({
  ia: 1,
  i9: 1,
  ae: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$FloatManifest$;
function $m_s_reflect_ManifestFactory$FloatManifest$() {
  if ((!$n_s_reflect_ManifestFactory$FloatManifest$)) {
    $n_s_reflect_ManifestFactory$FloatManifest$ = new $c_s_reflect_ManifestFactory$FloatManifest$();
  }
  return $n_s_reflect_ManifestFactory$FloatManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$IntManifest$() {
  this.av = null;
  this.av = "Int";
}
$p = $c_s_reflect_ManifestFactory$IntManifest$.prototype = new $h_s_reflect_ManifestFactory$IntManifest();
$p.constructor = $c_s_reflect_ManifestFactory$IntManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$IntManifest$() {
}
$h_s_reflect_ManifestFactory$IntManifest$.prototype = $p;
var $d_s_reflect_ManifestFactory$IntManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$IntManifest$, "scala.reflect.ManifestFactory$IntManifest$", ({
  ic: 1,
  ib: 1,
  ae: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$IntManifest$;
function $m_s_reflect_ManifestFactory$IntManifest$() {
  if ((!$n_s_reflect_ManifestFactory$IntManifest$)) {
    $n_s_reflect_ManifestFactory$IntManifest$ = new $c_s_reflect_ManifestFactory$IntManifest$();
  }
  return $n_s_reflect_ManifestFactory$IntManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$LongManifest$() {
  this.av = null;
  this.av = "Long";
}
$p = $c_s_reflect_ManifestFactory$LongManifest$.prototype = new $h_s_reflect_ManifestFactory$LongManifest();
$p.constructor = $c_s_reflect_ManifestFactory$LongManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$LongManifest$() {
}
$h_s_reflect_ManifestFactory$LongManifest$.prototype = $p;
var $d_s_reflect_ManifestFactory$LongManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$LongManifest$, "scala.reflect.ManifestFactory$LongManifest$", ({
  ie: 1,
  id: 1,
  ae: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$LongManifest$;
function $m_s_reflect_ManifestFactory$LongManifest$() {
  if ((!$n_s_reflect_ManifestFactory$LongManifest$)) {
    $n_s_reflect_ManifestFactory$LongManifest$ = new $c_s_reflect_ManifestFactory$LongManifest$();
  }
  return $n_s_reflect_ManifestFactory$LongManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$NothingManifest$() {
  this.ej = null;
  this.ej = "Nothing";
  $m_sci_Nil$();
}
$p = $c_s_reflect_ManifestFactory$NothingManifest$.prototype = new $h_s_reflect_ManifestFactory$PhantomManifest();
$p.constructor = $c_s_reflect_ManifestFactory$NothingManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$NothingManifest$() {
}
$h_s_reflect_ManifestFactory$NothingManifest$.prototype = $p;
$p.bk = (function() {
  return $d_sr_Nothing$.l();
});
$p.bU = (function(len) {
  return new ($d_sr_Nothing$.r().C)(len);
});
var $d_s_reflect_ManifestFactory$NothingManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$NothingManifest$, "scala.reflect.ManifestFactory$NothingManifest$", ({
  ig: 1,
  aL: 1,
  aK: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$NothingManifest$;
function $m_s_reflect_ManifestFactory$NothingManifest$() {
  if ((!$n_s_reflect_ManifestFactory$NothingManifest$)) {
    $n_s_reflect_ManifestFactory$NothingManifest$ = new $c_s_reflect_ManifestFactory$NothingManifest$();
  }
  return $n_s_reflect_ManifestFactory$NothingManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$NullManifest$() {
  this.ej = null;
  this.ej = "Null";
  $m_sci_Nil$();
}
$p = $c_s_reflect_ManifestFactory$NullManifest$.prototype = new $h_s_reflect_ManifestFactory$PhantomManifest();
$p.constructor = $c_s_reflect_ManifestFactory$NullManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$NullManifest$() {
}
$h_s_reflect_ManifestFactory$NullManifest$.prototype = $p;
$p.bk = (function() {
  return $d_sr_Null$.l();
});
$p.bU = (function(len) {
  return new ($d_sr_Null$.r().C)(len);
});
var $d_s_reflect_ManifestFactory$NullManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$NullManifest$, "scala.reflect.ManifestFactory$NullManifest$", ({
  ih: 1,
  aL: 1,
  aK: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$NullManifest$;
function $m_s_reflect_ManifestFactory$NullManifest$() {
  if ((!$n_s_reflect_ManifestFactory$NullManifest$)) {
    $n_s_reflect_ManifestFactory$NullManifest$ = new $c_s_reflect_ManifestFactory$NullManifest$();
  }
  return $n_s_reflect_ManifestFactory$NullManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$ObjectManifest$() {
  this.ej = null;
  this.ej = "Object";
  $m_sci_Nil$();
}
$p = $c_s_reflect_ManifestFactory$ObjectManifest$.prototype = new $h_s_reflect_ManifestFactory$PhantomManifest();
$p.constructor = $c_s_reflect_ManifestFactory$ObjectManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$ObjectManifest$() {
}
$h_s_reflect_ManifestFactory$ObjectManifest$.prototype = $p;
$p.bk = (function() {
  return $d_O.l();
});
$p.bU = (function(len) {
  return new $ac_O(len);
});
var $d_s_reflect_ManifestFactory$ObjectManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$ObjectManifest$, "scala.reflect.ManifestFactory$ObjectManifest$", ({
  ii: 1,
  aL: 1,
  aK: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$ObjectManifest$;
function $m_s_reflect_ManifestFactory$ObjectManifest$() {
  if ((!$n_s_reflect_ManifestFactory$ObjectManifest$)) {
    $n_s_reflect_ManifestFactory$ObjectManifest$ = new $c_s_reflect_ManifestFactory$ObjectManifest$();
  }
  return $n_s_reflect_ManifestFactory$ObjectManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$ShortManifest$() {
  this.av = null;
  this.av = "Short";
}
$p = $c_s_reflect_ManifestFactory$ShortManifest$.prototype = new $h_s_reflect_ManifestFactory$ShortManifest();
$p.constructor = $c_s_reflect_ManifestFactory$ShortManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$ShortManifest$() {
}
$h_s_reflect_ManifestFactory$ShortManifest$.prototype = $p;
var $d_s_reflect_ManifestFactory$ShortManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$ShortManifest$, "scala.reflect.ManifestFactory$ShortManifest$", ({
  ik: 1,
  ij: 1,
  ae: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$ShortManifest$;
function $m_s_reflect_ManifestFactory$ShortManifest$() {
  if ((!$n_s_reflect_ManifestFactory$ShortManifest$)) {
    $n_s_reflect_ManifestFactory$ShortManifest$ = new $c_s_reflect_ManifestFactory$ShortManifest$();
  }
  return $n_s_reflect_ManifestFactory$ShortManifest$;
}
/** @constructor */
function $c_s_reflect_ManifestFactory$UnitManifest$() {
  this.av = null;
  this.av = "Unit";
}
$p = $c_s_reflect_ManifestFactory$UnitManifest$.prototype = new $h_s_reflect_ManifestFactory$UnitManifest();
$p.constructor = $c_s_reflect_ManifestFactory$UnitManifest$;
/** @constructor */
function $h_s_reflect_ManifestFactory$UnitManifest$() {
}
$h_s_reflect_ManifestFactory$UnitManifest$.prototype = $p;
var $d_s_reflect_ManifestFactory$UnitManifest$ = new $TypeData().i($c_s_reflect_ManifestFactory$UnitManifest$, "scala.reflect.ManifestFactory$UnitManifest$", ({
  im: 1,
  il: 1,
  ae: 1,
  a: 1,
  V: 1,
  U: 1,
  d: 1,
  O: 1,
  a0: 1
}));
var $n_s_reflect_ManifestFactory$UnitManifest$;
function $m_s_reflect_ManifestFactory$UnitManifest$() {
  if ((!$n_s_reflect_ManifestFactory$UnitManifest$)) {
    $n_s_reflect_ManifestFactory$UnitManifest$ = new $c_s_reflect_ManifestFactory$UnitManifest$();
  }
  return $n_s_reflect_ManifestFactory$UnitManifest$;
}
function $f_sc_Seq__equals__O__Z($thiz, o) {
  if (($thiz === o)) {
    return true;
  } else {
    if ($is_sc_Seq(o)) {
      if (o.gn($thiz)) {
        return $thiz.f3(o);
      }
    }
    return false;
  }
}
function $is_sc_Seq(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.o)));
}
function $isArrayOf_sc_Seq(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.o)));
}
function $ct_sc_SeqView$Sorted__sc_SeqOps__I__s_math_Ordering__($thiz, underlying_, len, ord) {
  $thiz.dA = len;
  $thiz.ga = ord;
  $thiz.gZ = underlying_;
  $thiz.jM = false;
  return $thiz;
}
function $ct_sc_SeqView$Sorted__sc_SeqOps__s_math_Ordering__($thiz, underlying, ord) {
  $ct_sc_SeqView$Sorted__sc_SeqOps__I__s_math_Ordering__($thiz, underlying, underlying.l(), ord);
  return $thiz;
}
/** @constructor */
function $c_sc_SeqView$Sorted() {
  this.dA = 0;
  this.ga = null;
  this.gZ = null;
  this.jM = false;
  this.mK = null;
  this.mL = false;
}
$p = $c_sc_SeqView$Sorted.prototype = new $h_O();
$p.constructor = $c_sc_SeqView$Sorted;
/** @constructor */
function $h_sc_SeqView$Sorted() {
}
$h_sc_SeqView$Sorted.prototype = $p;
$p.fC = (function(p) {
  return $f_sc_IterableOnceOps__exists__F1__Z(this, p);
});
$p.d4 = (function(z, op) {
  return $f_sc_IterableOnceOps__foldLeft__O__F2__O(this, z, op);
});
$p.bc = (function(dest, start, n) {
  return $f_sc_IterableOnceOps__copyToArray__O__I__I__I(this, dest, start, n);
});
$p.cl = (function(b, start, sep, end) {
  return $f_sc_IterableOnceOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder(this, b, start, sep, end);
});
$p.c2 = (function() {
  $m_sci_List$();
  return $m_sci_Nil$().cc(this);
});
$p.m = (function() {
  return this.c().f();
});
$p.cb = (function(f) {
  return $f_sc_SeqOps__distinctBy__F1__O(this, f);
});
$p.bj = (function() {
  return new $c_sc_SeqView$Sorted$ReverseSorted(this).c();
});
$p.dU = (function(p, from) {
  return $f_sc_Iterator__indexWhere__F1__I__I(this.c(), p, from);
});
$p.aI = (function(len) {
  return $f_sc_IterableOps__sizeCompare__I__I(this, len);
});
$p.cI = (function() {
  return $m_sc_View$().T();
});
$p.bK = (function() {
  return "SeqView";
});
$p.aH = (function() {
  return $m_sc_View$();
});
$p.t = (function() {
  return $f_sc_View__toString__T(this);
});
$p.j5 = (function() {
  if ((!this.mL)) {
    var len = this.dA;
    if ((len === 0)) {
      var res = $m_sci_Nil$();
    } else if ((len === 1)) {
      $m_sci_List$();
      var elems = new $c_sjsr_WrappedVarArgs([this.gZ.m()]);
      var res = $m_sci_Nil$().cc(elems);
    } else {
      var arr = new $ac_O(len);
      this.gZ.bc(arr, 0, 2147483647);
      $m_ju_Arrays$().gz(arr, this.ga);
      var res = $m_sci_ArraySeq$().gB(arr);
    }
    this.jM = true;
    this.gZ = null;
    this.mK = res;
    this.mL = true;
  }
  return this.mK;
});
$p.oM = (function() {
  return (this.jM ? this.j5() : this.gZ);
});
$p.r = (function(i) {
  return this.j5().r(i);
});
$p.l = (function() {
  return this.dA;
});
$p.c = (function() {
  return $m_sc_Iterator$().G.fB(new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => this.j5().c())));
});
$p.q = (function() {
  return this.dA;
});
$p.g = (function() {
  return (this.dA === 0);
});
$p.l7 = (function(ord1) {
  var x$2 = this.ga;
  if (((ord1 === null) ? (x$2 === null) : ord1.p(x$2))) {
    return this;
  } else {
    return (ord1.dV(this.ga) ? new $c_sc_SeqView$Sorted$ReverseSorted(this) : $ct_sc_SeqView$Sorted__sc_SeqOps__I__s_math_Ordering__(new $c_sc_SeqView$Sorted(), this.oM(), this.dA, ord1));
  }
});
$p.bH = (function(ord) {
  return this.l7(ord);
});
$p.en = (function(coll) {
  return $m_sc_View$().iT(coll);
});
var $d_sc_SeqView$Sorted = new $TypeData().i($c_sc_SeqView$Sorted, "scala.collection.SeqView$Sorted", ({
  fo: 1,
  b: 1,
  c: 1,
  g: 1,
  k: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  a2: 1
}));
function $p_sc_SeqView$Sorted$ReverseSorted___reversed__sc_SeqView$Reverse($thiz) {
  if ((!$thiz.mN)) {
    $thiz.mM = $ct_sc_SeqView$Reverse__sc_SeqOps__(new $c_sc_SeqView$Reverse(), $thiz.d9.j5());
    $thiz.mN = true;
  }
  return $thiz.mM;
}
/** @constructor */
function $c_sc_SeqView$Sorted$ReverseSorted(outer) {
  this.mM = null;
  this.mN = false;
  this.d9 = null;
  if ((outer === null)) {
    throw $ct_jl_NullPointerException__(new $c_jl_NullPointerException());
  }
  this.d9 = outer;
}
$p = $c_sc_SeqView$Sorted$ReverseSorted.prototype = new $h_O();
$p.constructor = $c_sc_SeqView$Sorted$ReverseSorted;
/** @constructor */
function $h_sc_SeqView$Sorted$ReverseSorted() {
}
$h_sc_SeqView$Sorted$ReverseSorted.prototype = $p;
$p.fC = (function(p) {
  return $f_sc_IterableOnceOps__exists__F1__Z(this, p);
});
$p.d4 = (function(z, op) {
  return $f_sc_IterableOnceOps__foldLeft__O__F2__O(this, z, op);
});
$p.bc = (function(dest, start, n) {
  return $f_sc_IterableOnceOps__copyToArray__O__I__I__I(this, dest, start, n);
});
$p.cl = (function(b, start, sep, end) {
  return $f_sc_IterableOnceOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder(this, b, start, sep, end);
});
$p.c2 = (function() {
  $m_sci_List$();
  return $m_sci_Nil$().cc(this);
});
$p.m = (function() {
  return this.c().f();
});
$p.cb = (function(f) {
  return $f_sc_SeqOps__distinctBy__F1__O(this, f);
});
$p.bj = (function() {
  return this.d9.c();
});
$p.dU = (function(p, from) {
  return $f_sc_Iterator__indexWhere__F1__I__I(this.c(), p, from);
});
$p.aI = (function(len) {
  return $f_sc_IterableOps__sizeCompare__I__I(this, len);
});
$p.cI = (function() {
  return $m_sc_View$().T();
});
$p.bK = (function() {
  return "SeqView";
});
$p.aH = (function() {
  return $m_sc_View$();
});
$p.t = (function() {
  return $f_sc_View__toString__T(this);
});
$p.r = (function(i) {
  return $p_sc_SeqView$Sorted$ReverseSorted___reversed__sc_SeqView$Reverse(this).r(i);
});
$p.l = (function() {
  return this.d9.dA;
});
$p.c = (function() {
  return $m_sc_Iterator$().G.fB(new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => $p_sc_SeqView$Sorted$ReverseSorted___reversed__sc_SeqView$Reverse(this).c())));
});
$p.q = (function() {
  return this.d9.dA;
});
$p.g = (function() {
  return (this.d9.dA === 0);
});
$p.l7 = (function(ord1) {
  var x$2 = this.d9.ga;
  if (((ord1 === null) ? (x$2 === null) : ord1.p(x$2))) {
    return this.d9;
  } else {
    return (ord1.dV(this.d9.ga) ? this : $ct_sc_SeqView$Sorted__sc_SeqOps__I__s_math_Ordering__(new $c_sc_SeqView$Sorted(), this.d9.oM(), this.d9.dA, ord1));
  }
});
$p.bH = (function(ord) {
  return this.l7(ord);
});
$p.en = (function(coll) {
  return $m_sc_View$().iT(coll);
});
var $d_sc_SeqView$Sorted$ReverseSorted = new $TypeData().i($c_sc_SeqView$Sorted$ReverseSorted, "scala.collection.SeqView$Sorted$ReverseSorted", ({
  fp: 1,
  b: 1,
  c: 1,
  g: 1,
  k: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  a2: 1
}));
/** @constructor */
function $c_sc_View$$anon$1(it$3) {
  this.mP = null;
  this.mP = it$3;
}
$p = $c_sc_View$$anon$1.prototype = new $h_sc_AbstractView();
$p.constructor = $c_sc_View$$anon$1;
/** @constructor */
function $h_sc_View$$anon$1() {
}
$h_sc_View$$anon$1.prototype = $p;
$p.c = (function() {
  return this.mP.a2();
});
var $d_sc_View$$anon$1 = new $TypeData().i($c_sc_View$$anon$1, "scala.collection.View$$anon$1", ({
  fv: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1
}));
/** @constructor */
function $c_sc_View$Collect(underlying, pf) {
  this.mR = null;
  this.mQ = null;
  this.mR = underlying;
  this.mQ = pf;
}
$p = $c_sc_View$Collect.prototype = new $h_sc_AbstractView();
$p.constructor = $c_sc_View$Collect;
/** @constructor */
function $h_sc_View$Collect() {
}
$h_sc_View$Collect.prototype = $p;
$p.c = (function() {
  var this$1 = this.mR.c();
  return new $c_sc_Iterator$$anon$7(this.mQ, this$1);
});
var $d_sc_View$Collect = new $TypeData().i($c_sc_View$Collect, "scala.collection.View$Collect", ({
  fw: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1
}));
/** @constructor */
function $c_sc_View$DistinctBy(underlying, f) {
  this.ip = null;
  this.mS = null;
  this.ip = underlying;
  this.mS = f;
}
$p = $c_sc_View$DistinctBy.prototype = new $h_sc_AbstractView();
$p.constructor = $c_sc_View$DistinctBy;
/** @constructor */
function $h_sc_View$DistinctBy() {
}
$h_sc_View$DistinctBy.prototype = $p;
$p.c = (function() {
  var this$1 = this.ip.c();
  return new $c_sc_Iterator$$anon$8(this.mS, this$1);
});
$p.q = (function() {
  return ((this.ip.q() === 0) ? 0 : (-1));
});
$p.g = (function() {
  return this.ip.g();
});
var $d_sc_View$DistinctBy = new $TypeData().i($c_sc_View$DistinctBy, "scala.collection.View$DistinctBy", ({
  fx: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1
}));
function $ct_sc_View$Map__sc_IterableOps__F1__($thiz, underlying, f) {
  $thiz.fh = underlying;
  $thiz.h1 = f;
  return $thiz;
}
/** @constructor */
function $c_sc_View$Map() {
  this.fh = null;
  this.h1 = null;
}
$p = $c_sc_View$Map.prototype = new $h_sc_AbstractView();
$p.constructor = $c_sc_View$Map;
/** @constructor */
function $h_sc_View$Map() {
}
$h_sc_View$Map.prototype = $p;
$p.c = (function() {
  var this$1 = this.fh.c();
  return new $c_sc_Iterator$$anon$9(this.h1, this$1);
});
$p.q = (function() {
  return this.fh.q();
});
$p.g = (function() {
  return this.fh.g();
});
var $d_sc_View$Map = new $TypeData().i($c_sc_View$Map, "scala.collection.View$Map", ({
  aY: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1
}));
function $ct_sc_View$Prepended__O__sc_IterableOps__($thiz, elem, underlying) {
  $thiz.jN = elem;
  $thiz.jO = underlying;
  return $thiz;
}
/** @constructor */
function $c_sc_View$Prepended() {
  this.jN = null;
  this.jO = null;
}
$p = $c_sc_View$Prepended.prototype = new $h_sc_AbstractView();
$p.constructor = $c_sc_View$Prepended;
/** @constructor */
function $h_sc_View$Prepended() {
}
$h_sc_View$Prepended.prototype = $p;
/** @constructor */
function $c_sc_View$ZipWithIndex(underlying) {
  this.iq = null;
  this.iq = underlying;
}
$p = $c_sc_View$ZipWithIndex.prototype = new $h_sc_AbstractView();
$p.constructor = $c_sc_View$ZipWithIndex;
/** @constructor */
function $h_sc_View$ZipWithIndex() {
}
$h_sc_View$ZipWithIndex.prototype = $p;
$p.c = (function() {
  return new $c_sc_Iterator$$anon$16(this.iq.c());
});
$p.q = (function() {
  return this.iq.q();
});
$p.g = (function() {
  return this.iq.g();
});
var $d_sc_View$ZipWithIndex = new $TypeData().i($c_sc_View$ZipWithIndex, "scala.collection.View$ZipWithIndex", ({
  fz: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1
}));
/** @constructor */
function $c_Lorg_scalajs_dom_DOMList$DOMListSeq(domList) {
  this.i9 = null;
  this.i9 = domList;
}
$p = $c_Lorg_scalajs_dom_DOMList$DOMListSeq.prototype = new $h_O();
$p.constructor = $c_Lorg_scalajs_dom_DOMList$DOMListSeq;
/** @constructor */
function $h_Lorg_scalajs_dom_DOMList$DOMListSeq() {
}
$h_Lorg_scalajs_dom_DOMList$DOMListSeq.prototype = $p;
$p.q = (function() {
  return (-1);
});
$p.fC = (function(p) {
  return $f_sc_IterableOnceOps__exists__F1__Z(this, p);
});
$p.d4 = (function(z, op) {
  return $f_sc_IterableOnceOps__foldLeft__O__F2__O(this, z, op);
});
$p.bc = (function(xs, start, len) {
  return $f_sc_IterableOnceOps__copyToArray__O__I__I__I(this, xs, start, len);
});
$p.cl = (function(b, start, sep, end) {
  return $f_sc_IterableOnceOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder(this, b, start, sep, end);
});
$p.c2 = (function() {
  $m_sci_List$();
  return $m_sci_Nil$().cc(this);
});
$p.m = (function() {
  return this.c().f();
});
$p.cI = (function() {
  return $m_sc_Seq$().T();
});
$p.bK = (function() {
  return "Seq";
});
$p.bQ = (function(x, default$1) {
  return $f_s_PartialFunction__applyOrElse__O__F1__O(this, x, default$1);
});
$p.cb = (function(f) {
  return $f_sc_SeqOps__distinctBy__F1__O(this, f);
});
$p.bj = (function() {
  return $f_sc_IterableOnceOps__reversed__sc_Iterable(this).c();
});
$p.dU = (function(p, from) {
  return $f_sc_Iterator__indexWhere__F1__I__I(this.c(), p, from);
});
$p.bH = (function(ord) {
  return $f_sc_SeqOps__sorted__s_math_Ordering__O(this, ord);
});
$p.aI = (function(len) {
  return $f_sc_IterableOps__sizeCompare__I__I(this, len);
});
$p.g = (function() {
  return $f_sc_SeqOps__isEmpty__Z(this);
});
$p.f3 = (function(that) {
  return $f_sc_SeqOps__sameElements__sc_IterableOnce__Z(this, that);
});
$p.gn = (function(that) {
  return true;
});
$p.p = (function(o) {
  return $f_sc_Seq__equals__O__Z(this, o);
});
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().l5(this);
});
$p.t = (function() {
  return $f_sc_Iterable__toString__T(this);
});
$p.l = (function() {
  return (this.i9.length | 0);
});
$p.r = (function(x) {
  return this.i9[x];
});
$p.c = (function() {
  return new $c_Lorg_scalajs_dom_DOMList$DOMListIterator(this.i9);
});
$p.d = (function(v1) {
  return this.r((v1 | 0));
});
$p.aH = (function() {
  return $m_sc_Seq$();
});
$p.cF = (function(x) {
  return $f_sc_SeqOps__isDefinedAt__I__Z(this, (x | 0));
});
$p.en = (function(coll) {
  return $m_sc_Seq$().fD(coll);
});
$p.f1 = (function(v1) {
  return (this.r(v1) | 0);
});
var $d_Lorg_scalajs_dom_DOMList$DOMListSeq = new $TypeData().i($c_Lorg_scalajs_dom_DOMList$DOMListSeq, "org.scalajs.dom.DOMList$DOMListSeq", ({
  eB: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1
}));
/** @constructor */
function $c_sc_AbstractSet() {
}
$p = $c_sc_AbstractSet.prototype = new $h_sc_AbstractIterable();
$p.constructor = $c_sc_AbstractSet;
/** @constructor */
function $h_sc_AbstractSet() {
}
$h_sc_AbstractSet.prototype = $p;
$p.l9 = (function(that) {
  return this.dN(that);
});
$p.p = (function(that) {
  return $f_sc_Set__equals__O__Z(this, that);
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.gA(this, this$1.iI);
});
$p.bl = (function() {
  return "Set";
});
$p.t = (function() {
  return $f_sc_Iterable__toString__T(this);
});
$p.d = (function(v1) {
  return this.bb(v1);
});
function $f_sc_Map__equals__O__Z($thiz, o) {
  if (($thiz === o)) {
    return true;
  } else if ($is_sc_Map(o)) {
    if (($thiz.M() === o.M())) {
      try {
        return $thiz.dN(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((map$1) => ((kv$2) => $m_sr_BoxesRunTime$().n(map$1.eo(kv$2.aw(), new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => $m_sc_Map$().mJ.a2()))), kv$2.ap())))(o)));
      } catch (e) {
        if (false) {
          return false;
        } else {
          throw e;
        }
      }
    } else {
      return false;
    }
  } else {
    return false;
  }
}
function $is_sc_Map(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.a8)));
}
function $isArrayOf_sc_Map(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.a8)));
}
function $ct_s_concurrent_impl_Promise$Transformation__F1__s_concurrent_ExecutionContext__s_util_Try__I__($thiz, _fun, _ec, _arg, _xform) {
  $thiz.iC = _fun;
  $thiz.hm = _ec;
  $thiz.hl = _arg;
  $thiz.hn = _xform;
  $ct_s_concurrent_impl_Promise$DefaultPromise__($thiz);
  return $thiz;
}
function $ct_s_concurrent_impl_Promise$Transformation__I__F1__s_concurrent_ExecutionContext__($thiz, xform, f, ec) {
  $ct_s_concurrent_impl_Promise$Transformation__F1__s_concurrent_ExecutionContext__s_util_Try__I__($thiz, f, ec, null, xform);
  return $thiz;
}
function $p_s_concurrent_impl_Promise$Transformation__handleFailure__jl_Throwable__s_concurrent_ExecutionContext__V($thiz, t, e) {
  if ((false || $m_s_util_control_NonFatal$().iR(t))) {
    var completed = $thiz.fL($thiz.ab, $m_s_concurrent_impl_Promise$().hS(new $c_s_util_Failure(t)));
    if (((($thiz.hn === 5) || ($thiz.hn === 6)) || (!completed))) {
      e.l2(t);
    }
  } else {
    var $x_1 = t;
    throw (($x_1 instanceof $c_sjs_js_JavaScriptException) ? $x_1.di : $x_1);
  }
}
/** @constructor */
function $c_s_concurrent_impl_Promise$Transformation() {
  this.ab = null;
  this.iC = null;
  this.hm = null;
  this.hl = null;
  this.hn = 0;
}
$p = $c_s_concurrent_impl_Promise$Transformation.prototype = new $h_s_concurrent_impl_Promise$DefaultPromise();
$p.constructor = $c_s_concurrent_impl_Promise$Transformation;
/** @constructor */
function $h_s_concurrent_impl_Promise$Transformation() {
}
$h_s_concurrent_impl_Promise$Transformation.prototype = $p;
$p.oX = (function(resolved) {
  this.hl = resolved;
  var e = this.hm;
  try {
    if ((e === null)) {
      $m_sr_Scala3RunTime$().bV();
    }
    e.kB(this);
  } catch (e$2) {
    var e$3 = ((e$2 instanceof $c_jl_Throwable) ? e$2 : new $c_sjs_js_JavaScriptException(e$2));
    this.iC = null;
    this.hl = null;
    this.hm = null;
    if ((e === null)) {
      $m_sr_Scala3RunTime$().bV();
    }
    $p_s_concurrent_impl_Promise$Transformation__handleFailure__jl_Throwable__s_concurrent_ExecutionContext__V(this, e$3, e);
  }
  return this;
});
$p.fJ = (function() {
  var x$proxy4 = this.hl;
  if ((x$proxy4 === null)) {
    $m_sr_Scala3RunTime$().bV();
  }
  var x$proxy5 = this.iC;
  if ((x$proxy5 === null)) {
    $m_sr_Scala3RunTime$().bV();
  }
  var x$proxy6 = this.hm;
  if ((x$proxy6 === null)) {
    $m_sr_Scala3RunTime$().bV();
  }
  this.iC = null;
  this.hl = null;
  this.hm = null;
  try {
    var resolvedResult;
    switch (this.hn) {
      case 0: {
        var resolvedResult = null;
        break;
      }
      case 1: {
        var resolvedResult = ((x$proxy4 instanceof $c_s_util_Success) ? new $c_s_util_Success(x$proxy5.d(x$proxy4.S())) : x$proxy4);
        break;
      }
      case 2: {
        if ((x$proxy4 instanceof $c_s_util_Success)) {
          var f = x$proxy5.d(x$proxy4.S());
          if ((f instanceof $c_s_concurrent_impl_Promise$DefaultPromise)) {
            f.kZ(this, null);
          } else {
            this.kt(f);
          }
          var resolvedResult = null;
        } else {
          var resolvedResult = x$proxy4;
        }
        break;
      }
      case 3: {
        var resolvedResult = $m_s_concurrent_impl_Promise$().hS(x$proxy5.d(x$proxy4));
        break;
      }
      case 4: {
        var f$2 = x$proxy5.d(x$proxy4);
        if ((f$2 instanceof $c_s_concurrent_impl_Promise$DefaultPromise)) {
          f$2.kZ(this, null);
        } else {
          this.kt(f$2);
        }
        var resolvedResult = null;
        break;
      }
      case 5: {
        x$proxy4.dO(x$proxy5);
        var resolvedResult = null;
        break;
      }
      case 6: {
        x$proxy5.d(x$proxy4);
        var resolvedResult = null;
        break;
      }
      case 7: {
        var resolvedResult = ((x$proxy4 instanceof $c_s_util_Failure) ? $m_s_concurrent_impl_Promise$().hS(x$proxy4.oF(x$proxy5)) : x$proxy4);
        break;
      }
      case 8: {
        if ((x$proxy4 instanceof $c_s_util_Failure)) {
          var f$3 = x$proxy5.bQ(x$proxy4.f0, $m_s_concurrent_Future$().nn);
          var resolvedResult = ((f$3 !== $m_s_concurrent_Future$().k7) ? (((f$3 instanceof $c_s_concurrent_impl_Promise$DefaultPromise) ? f$3.kZ(this, null) : this.kt(f$3)), null) : x$proxy4);
        } else {
          var resolvedResult = x$proxy4;
        }
        break;
      }
      case 9: {
        var resolvedResult = (((x$proxy4 instanceof $c_s_util_Failure) || (!(!x$proxy5.d(x$proxy4.S())))) ? x$proxy4 : $m_s_concurrent_Future$().nm);
        break;
      }
      case 10: {
        var resolvedResult = ((x$proxy4 instanceof $c_s_util_Success) ? new $c_s_util_Success(x$proxy5.bQ(x$proxy4.S(), $m_s_concurrent_Future$().nk)) : x$proxy4);
        break;
      }
      default: {
        var resolvedResult = new $c_s_util_Failure($ct_jl_IllegalStateException__T__(new $c_jl_IllegalStateException(), ("BUG: encountered transformation promise with illegal type: " + this.hn)));
      }
    }
    if ((resolvedResult !== null)) {
      this.fL(this.ab, resolvedResult);
    }
  } catch (e) {
    $p_s_concurrent_impl_Promise$Transformation__handleFailure__jl_Throwable__s_concurrent_ExecutionContext__V(this, ((e instanceof $c_jl_Throwable) ? e : new $c_sjs_js_JavaScriptException(e)), x$proxy6);
  }
});
function $isArrayOf_s_concurrent_impl_Promise$Transformation(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cS)));
}
var $d_s_concurrent_impl_Promise$Transformation = new $TypeData().i($c_s_concurrent_impl_Promise$Transformation, "scala.concurrent.impl.Promise$Transformation", ({
  cS: 1,
  ba: 1,
  aU: 1,
  a: 1,
  cM: 1,
  cJ: 1,
  cL: 1,
  i: 1,
  aJ: 1,
  aS: 1,
  hk: 1
}));
/** @constructor */
function $c_sr_RichLong(self_$_lo, self_$_hi) {
  this.fy = 0;
  this.fz = 0;
  this.fy = self_$_lo;
  this.fz = self_$_hi;
}
$p = $c_sr_RichLong.prototype = new $h_O();
$p.constructor = $c_sr_RichLong;
/** @constructor */
function $h_sr_RichLong() {
}
$h_sr_RichLong.prototype = $p;
$p.t = (function() {
  return $f_s_Proxy__toString__T(this);
});
$p.ks = (function(that) {
  var x_$_lo = this.fy;
  var x_$_hi = this.fz;
  var $x_1 = $uJ(that);
  var y_$_lo = $x_1.l;
  var y_$_hi = $x_1.h;
  return ((x_$_hi === y_$_hi) ? ((x_$_lo === y_$_lo) ? 0 : (((x_$_lo >>> 0) < (y_$_lo >>> 0)) ? (-1) : 1)) : ((x_$_hi < y_$_hi) ? (-1) : 1));
});
$p.u = (function() {
  var this$_$_lo = this.fy;
  var this$_$_hi = this.fz;
  return (this$_$_lo ^ this$_$_hi);
});
$p.p = (function(x$0) {
  return $m_sr_RichLong$().qA(this.fy, this.fz, x$0);
});
function $isArrayOf_sr_RichLong(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cY)));
}
var $d_sr_RichLong = new $TypeData().i($c_sr_RichLong, "scala.runtime.RichLong", ({
  cY: 1,
  hX: 1,
  eW: 1,
  eX: 1,
  F: 1,
  bb: 1,
  iD: 1,
  iH: 1,
  iK: 1,
  iE: 1,
  ix: 1
}));
/** @constructor */
function $c_sc_AbstractSeq() {
}
$p = $c_sc_AbstractSeq.prototype = new $h_sc_AbstractIterable();
$p.constructor = $c_sc_AbstractSeq;
/** @constructor */
function $h_sc_AbstractSeq() {
}
$h_sc_AbstractSeq.prototype = $p;
$p.f1 = (function(x$0) {
  return (this.d(x$0) | 0);
});
$p.bQ = (function(x, default$1) {
  return $f_s_PartialFunction__applyOrElse__O__F1__O(this, x, default$1);
});
$p.cb = (function(f) {
  return $f_sc_SeqOps__distinctBy__F1__O(this, f);
});
$p.bj = (function() {
  return this.d6().c();
});
$p.kS = (function(idx) {
  return $f_sc_SeqOps__isDefinedAt__I__Z(this, idx);
});
$p.dU = (function(p, from) {
  return $f_sc_Iterator__indexWhere__F1__I__I(this.c(), p, from);
});
$p.bH = (function(ord) {
  return $f_sc_SeqOps__sorted__s_math_Ordering__O(this, ord);
});
$p.aI = (function(len) {
  return $f_sc_IterableOps__sizeCompare__I__I(this, len);
});
$p.g = (function() {
  return $f_sc_SeqOps__isEmpty__Z(this);
});
$p.f3 = (function(that) {
  return $f_sc_SeqOps__sameElements__sc_IterableOnce__Z(this, that);
});
$p.gn = (function(that) {
  return true;
});
$p.p = (function(o) {
  return $f_sc_Seq__equals__O__Z(this, o);
});
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().l5(this);
});
$p.t = (function() {
  return $f_sc_Iterable__toString__T(this);
});
$p.cF = (function(x) {
  return this.kS((x | 0));
});
/** @constructor */
function $c_sc_AbstractSeqView() {
}
$p = $c_sc_AbstractSeqView.prototype = new $h_sc_AbstractView();
$p.constructor = $c_sc_AbstractSeqView;
/** @constructor */
function $h_sc_AbstractSeqView() {
}
$h_sc_AbstractSeqView.prototype = $p;
$p.cb = (function(f) {
  return $f_sc_SeqOps__distinctBy__F1__O(this, f);
});
$p.bj = (function() {
  return this.d6().c();
});
$p.dU = (function(p, from) {
  return $f_sc_Iterator__indexWhere__F1__I__I(this.c(), p, from);
});
$p.aI = (function(len) {
  return $f_sc_IterableOps__sizeCompare__I__I(this, len);
});
$p.g = (function() {
  return $f_sc_SeqOps__isEmpty__Z(this);
});
$p.bl = (function() {
  return "SeqView";
});
$p.bH = (function(ord) {
  return $ct_sc_SeqView$Sorted__sc_SeqOps__s_math_Ordering__(new $c_sc_SeqView$Sorted(), this, ord);
});
function $is_sc_IndexedSeq(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.t)));
}
function $isArrayOf_sc_IndexedSeq(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.t)));
}
function $is_sc_LinearSeq(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.aB)));
}
function $isArrayOf_sc_LinearSeq(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.aB)));
}
function $f_scm_CheckedIndexedSeqView__iterator__sc_Iterator($thiz) {
  return new $c_scm_CheckedIndexedSeqView$CheckedIterator($thiz, new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => ($thiz.oA().a2() | 0))));
}
function $f_scm_CheckedIndexedSeqView__reverseIterator__sc_Iterator($thiz) {
  return new $c_scm_CheckedIndexedSeqView$CheckedReverseIterator($thiz, new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => ($thiz.oA().a2() | 0))));
}
/** @constructor */
function $c_sc_AbstractMap() {
}
$p = $c_sc_AbstractMap.prototype = new $h_sc_AbstractIterable();
$p.constructor = $c_sc_AbstractMap;
/** @constructor */
function $h_sc_AbstractMap() {
}
$h_sc_AbstractMap.prototype = $p;
$p.f1 = (function(x$0) {
  return (this.d(x$0) | 0);
});
$p.bQ = (function(x, default$1) {
  return $f_sc_MapOps__applyOrElse__O__F1__O(this, x, default$1);
});
$p.dm = (function(f) {
  $f_sc_MapOps__foreachEntry__F2__V(this, f);
});
$p.cF = (function(key) {
  return this.bb(key);
});
$p.cl = (function(sb, start, sep, end) {
  return $f_sc_MapOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder(this, sb, start, sep, end);
});
$p.gr = (function(coll) {
  return this.j1().a3(coll);
});
$p.cI = (function() {
  return this.j1().T();
});
$p.p = (function(o) {
  return $f_sc_Map__equals__O__Z(this, o);
});
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().ru(this);
});
$p.bl = (function() {
  return "Map";
});
$p.t = (function() {
  return $f_sc_Iterable__toString__T(this);
});
function $ct_sc_SeqView$Id__sc_SeqOps__($thiz, underlying) {
  $thiz.fe = underlying;
  return $thiz;
}
/** @constructor */
function $c_sc_SeqView$Id() {
  this.fe = null;
}
$p = $c_sc_SeqView$Id.prototype = new $h_sc_AbstractSeqView();
$p.constructor = $c_sc_SeqView$Id;
/** @constructor */
function $h_sc_SeqView$Id() {
}
$h_sc_SeqView$Id.prototype = $p;
$p.r = (function(idx) {
  return this.fe.r(idx);
});
$p.l = (function() {
  return this.fe.l();
});
$p.c = (function() {
  return this.fe.c();
});
$p.q = (function() {
  return this.fe.q();
});
$p.g = (function() {
  return this.fe.g();
});
var $d_sc_SeqView$Id = new $TypeData().i($c_sc_SeqView$Id, "scala.collection.SeqView$Id", ({
  bT: 1,
  as: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  k: 1,
  a2: 1
}));
function $ct_sc_SeqView$Map__sc_SeqOps__F1__($thiz, underlying, f) {
  $thiz.gY = underlying;
  $thiz.im = f;
  $ct_sc_View$Map__sc_IterableOps__F1__($thiz, underlying, f);
  return $thiz;
}
/** @constructor */
function $c_sc_SeqView$Map() {
  this.fh = null;
  this.h1 = null;
  this.gY = null;
  this.im = null;
}
$p = $c_sc_SeqView$Map.prototype = new $h_sc_View$Map();
$p.constructor = $c_sc_SeqView$Map;
/** @constructor */
function $h_sc_SeqView$Map() {
}
$h_sc_SeqView$Map.prototype = $p;
$p.cb = (function(f) {
  return $f_sc_SeqOps__distinctBy__F1__O(this, f);
});
$p.dU = (function(p, from) {
  return $f_sc_Iterator__indexWhere__F1__I__I(this.c(), p, from);
});
$p.g = (function() {
  return $f_sc_SeqOps__isEmpty__Z(this);
});
$p.r = (function(idx) {
  return this.im.d(this.gY.r(idx));
});
$p.l = (function() {
  return this.gY.l();
});
$p.bH = (function(ord) {
  return $ct_sc_SeqView$Sorted__sc_SeqOps__s_math_Ordering__(new $c_sc_SeqView$Sorted(), this, ord);
});
function $ct_sc_SeqView$Prepended__O__sc_SeqOps__($thiz, elem, underlying) {
  $thiz.jL = elem;
  $thiz.io = underlying;
  $ct_sc_View$Prepended__O__sc_IterableOps__($thiz, elem, underlying);
  return $thiz;
}
/** @constructor */
function $c_sc_SeqView$Prepended() {
  this.jN = null;
  this.jO = null;
  this.jL = null;
  this.io = null;
}
$p = $c_sc_SeqView$Prepended.prototype = new $h_sc_View$Prepended();
$p.constructor = $c_sc_SeqView$Prepended;
/** @constructor */
function $h_sc_SeqView$Prepended() {
}
$h_sc_SeqView$Prepended.prototype = $p;
$p.cb = (function(f) {
  return $f_sc_SeqOps__distinctBy__F1__O(this, f);
});
$p.dU = (function(p, from) {
  return $f_sc_Iterator__indexWhere__F1__I__I(this.c(), p, from);
});
$p.g = (function() {
  return $f_sc_SeqOps__isEmpty__Z(this);
});
$p.r = (function(idx) {
  return ((idx === 0) ? this.jL : this.io.r(((idx - 1) | 0)));
});
$p.l = (function() {
  return ((1 + this.io.l()) | 0);
});
$p.bH = (function(ord) {
  return $ct_sc_SeqView$Sorted__sc_SeqOps__s_math_Ordering__(new $c_sc_SeqView$Sorted(), this, ord);
});
function $ct_sc_SeqView$Reverse__sc_SeqOps__($thiz, underlying) {
  $thiz.ff = underlying;
  return $thiz;
}
/** @constructor */
function $c_sc_SeqView$Reverse() {
  this.ff = null;
}
$p = $c_sc_SeqView$Reverse.prototype = new $h_sc_AbstractSeqView();
$p.constructor = $c_sc_SeqView$Reverse;
/** @constructor */
function $h_sc_SeqView$Reverse() {
}
$h_sc_SeqView$Reverse.prototype = $p;
$p.r = (function(i) {
  return this.ff.r(((((this.l() - 1) | 0) - i) | 0));
});
$p.l = (function() {
  return this.ff.l();
});
$p.c = (function() {
  return this.ff.bj();
});
$p.q = (function() {
  return this.ff.q();
});
$p.g = (function() {
  return this.ff.g();
});
var $d_sc_SeqView$Reverse = new $TypeData().i($c_sc_SeqView$Reverse, "scala.collection.SeqView$Reverse", ({
  bV: 1,
  as: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  k: 1,
  a2: 1
}));
function $is_sci_Seq(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.x)));
}
function $isArrayOf_sci_Seq(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.x)));
}
function $f_scm_MapOps__getOrElseUpdate__O__F0__O($thiz, key, defaultValue) {
  var x12 = $thiz.cn(key);
  if ((x12 instanceof $c_s_Some)) {
    return x12.cf;
  }
  if (($m_s_None$() === x12)) {
    var d = defaultValue.a2();
    $thiz.sp(key, d);
    return d;
  }
  throw new $c_s_MatchError(x12);
}
/** @constructor */
function $c_sc_AbstractMapView() {
}
$p = $c_sc_AbstractMapView.prototype = new $h_sc_AbstractView();
$p.constructor = $c_sc_AbstractMapView;
/** @constructor */
function $h_sc_AbstractMapView() {
}
$h_sc_AbstractMapView.prototype = $p;
$p.f1 = (function(x$0) {
  return ($f_sc_MapOps__apply__O__O(this, x$0) | 0);
});
$p.eo = (function(key, default$1) {
  return $f_sc_MapOps__getOrElse__O__F0__O(this, key, default$1);
});
$p.d = (function(key) {
  return $f_sc_MapOps__apply__O__O(this, key);
});
$p.bQ = (function(x, default$1) {
  return $f_sc_MapOps__applyOrElse__O__F1__O(this, x, default$1);
});
$p.cF = (function(key) {
  return $f_sc_MapOps__contains__O__Z(this, key);
});
$p.cl = (function(sb, start, sep, end) {
  return $f_sc_MapOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder(this, sb, start, sep, end);
});
$p.t = (function() {
  return $f_sc_View__toString__T(this);
});
$p.bl = (function() {
  return "MapView";
});
function $is_sci_Map(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.af)));
}
function $isArrayOf_sci_Map(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.af)));
}
/** @constructor */
function $c_sc_AbstractIndexedSeqView() {
}
$p = $c_sc_AbstractIndexedSeqView.prototype = new $h_sc_AbstractSeqView();
$p.constructor = $c_sc_AbstractIndexedSeqView;
/** @constructor */
function $h_sc_AbstractIndexedSeqView() {
}
$h_sc_AbstractIndexedSeqView.prototype = $p;
$p.d6 = (function() {
  return new $c_sc_IndexedSeqView$Reverse(this);
});
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var x = this.l();
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.q = (function() {
  return this.l();
});
$p.c = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), this);
});
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), this);
});
$p.f2 = (function(f) {
  return $ct_sc_IndexedSeqView$Map__sc_IndexedSeqOps__F1__(new $c_sc_IndexedSeqView$Map(), this, f);
});
$p.bl = (function() {
  return "IndexedSeqView";
});
$p.aa = (function(f) {
  return this.f2(f);
});
/** @constructor */
function $c_sc_MapView$Id(underlying) {
  this.gW = null;
  this.gW = underlying;
}
$p = $c_sc_MapView$Id.prototype = new $h_sc_AbstractMapView();
$p.constructor = $c_sc_MapView$Id;
/** @constructor */
function $h_sc_MapView$Id() {
}
$h_sc_MapView$Id.prototype = $p;
$p.cn = (function(key) {
  return this.gW.cn(key);
});
$p.c = (function() {
  return this.gW.c();
});
$p.q = (function() {
  return this.gW.q();
});
$p.g = (function() {
  return this.gW.g();
});
var $d_sc_MapView$Id = new $TypeData().i($c_sc_MapView$Id, "scala.collection.MapView$Id", ({
  fk: 1,
  bI: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  i: 1,
  j: 1,
  a9: 1,
  bS: 1
}));
/** @constructor */
function $c_sc_MapView$MapValues(underlying, f) {
  this.gX = null;
  this.jK = null;
  this.gX = underlying;
  this.jK = f;
}
$p = $c_sc_MapView$MapValues.prototype = new $h_sc_AbstractMapView();
$p.constructor = $c_sc_MapView$MapValues;
/** @constructor */
function $h_sc_MapView$MapValues() {
}
$h_sc_MapView$MapValues.prototype = $p;
$p.c = (function() {
  return new $c_sc_Iterator$$anon$9(new $c_sr_AbstractFunction1_$$Lambda$7afc3dd0acc1681fb022ef921c83979087aaa919(((kv$2) => $ct_T2__O__O__(new $c_T2(), kv$2.aw(), this.jK.d(kv$2.ap())))), this.gX.c());
});
$p.cn = (function(key) {
  var this$1 = this.gX.cn(key);
  var f = this.jK;
  return (this$1.g() ? $m_s_None$() : new $c_s_Some(f.d(this$1.S())));
});
$p.q = (function() {
  return this.gX.q();
});
$p.g = (function() {
  return this.gX.g();
});
var $d_sc_MapView$MapValues = new $TypeData().i($c_sc_MapView$MapValues, "scala.collection.MapView$MapValues", ({
  fl: 1,
  bI: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  i: 1,
  j: 1,
  a9: 1,
  bS: 1
}));
/** @constructor */
function $c_sci_AbstractSet() {
}
$p = $c_sci_AbstractSet.prototype = new $h_sc_AbstractSet();
$p.constructor = $c_sci_AbstractSet;
/** @constructor */
function $h_sci_AbstractSet() {
}
$h_sci_AbstractSet.prototype = $p;
$p.aH = (function() {
  return $m_sci_Set$();
});
/** @constructor */
function $c_sc_IndexedSeqView$Id(underlying) {
  this.fe = null;
  $ct_sc_SeqView$Id__sc_SeqOps__(this, underlying);
}
$p = $c_sc_IndexedSeqView$Id.prototype = new $h_sc_SeqView$Id();
$p.constructor = $c_sc_IndexedSeqView$Id;
/** @constructor */
function $h_sc_IndexedSeqView$Id() {
}
$h_sc_IndexedSeqView$Id.prototype = $p;
$p.d6 = (function() {
  return new $c_sc_IndexedSeqView$Reverse(this);
});
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var x = this.l();
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.q = (function() {
  return this.l();
});
$p.c = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), this);
});
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), this);
});
$p.bl = (function() {
  return "IndexedSeqView";
});
$p.aa = (function(f) {
  return $ct_sc_IndexedSeqView$Map__sc_IndexedSeqOps__F1__(new $c_sc_IndexedSeqView$Map(), this, f);
});
var $d_sc_IndexedSeqView$Id = new $TypeData().i($c_sc_IndexedSeqView$Id, "scala.collection.IndexedSeqView$Id", ({
  f5: 1,
  bT: 1,
  as: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  k: 1,
  a2: 1,
  n: 1,
  am: 1
}));
function $ct_sc_IndexedSeqView$Map__sc_IndexedSeqOps__F1__($thiz, underlying, f) {
  $ct_sc_SeqView$Map__sc_SeqOps__F1__($thiz, underlying, f);
  return $thiz;
}
/** @constructor */
function $c_sc_IndexedSeqView$Map() {
  this.fh = null;
  this.h1 = null;
  this.gY = null;
  this.im = null;
}
$p = $c_sc_IndexedSeqView$Map.prototype = new $h_sc_SeqView$Map();
$p.constructor = $c_sc_IndexedSeqView$Map;
/** @constructor */
function $h_sc_IndexedSeqView$Map() {
}
$h_sc_IndexedSeqView$Map.prototype = $p;
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var x = this.l();
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.q = (function() {
  return this.l();
});
$p.c = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), this);
});
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), this);
});
$p.f2 = (function(f) {
  return $ct_sc_IndexedSeqView$Map__sc_IndexedSeqOps__F1__(new $c_sc_IndexedSeqView$Map(), this, f);
});
$p.bl = (function() {
  return "IndexedSeqView";
});
$p.aa = (function(f) {
  return this.f2(f);
});
var $d_sc_IndexedSeqView$Map = new $TypeData().i($c_sc_IndexedSeqView$Map, "scala.collection.IndexedSeqView$Map", ({
  bO: 1,
  bU: 1,
  aY: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  k: 1,
  a2: 1,
  n: 1,
  am: 1
}));
/** @constructor */
function $c_sc_IndexedSeqView$Prepended(elem, underlying) {
  this.jN = null;
  this.jO = null;
  this.jL = null;
  this.io = null;
  $ct_sc_SeqView$Prepended__O__sc_SeqOps__(this, elem, underlying);
}
$p = $c_sc_IndexedSeqView$Prepended.prototype = new $h_sc_SeqView$Prepended();
$p.constructor = $c_sc_IndexedSeqView$Prepended;
/** @constructor */
function $h_sc_IndexedSeqView$Prepended() {
}
$h_sc_IndexedSeqView$Prepended.prototype = $p;
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var x = this.l();
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.q = (function() {
  return this.l();
});
$p.c = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), this);
});
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), this);
});
$p.f2 = (function(f) {
  return $ct_sc_IndexedSeqView$Map__sc_IndexedSeqOps__F1__(new $c_sc_IndexedSeqView$Map(), this, f);
});
$p.bl = (function() {
  return "IndexedSeqView";
});
$p.aa = (function(f) {
  return this.f2(f);
});
var $d_sc_IndexedSeqView$Prepended = new $TypeData().i($c_sc_IndexedSeqView$Prepended, "scala.collection.IndexedSeqView$Prepended", ({
  f6: 1,
  fn: 1,
  fy: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  k: 1,
  a2: 1,
  n: 1,
  am: 1
}));
/** @constructor */
function $c_sc_IndexedSeqView$Reverse(underlying) {
  this.ff = null;
  $ct_sc_SeqView$Reverse__sc_SeqOps__(this, underlying);
}
$p = $c_sc_IndexedSeqView$Reverse.prototype = new $h_sc_SeqView$Reverse();
$p.constructor = $c_sc_IndexedSeqView$Reverse;
/** @constructor */
function $h_sc_IndexedSeqView$Reverse() {
}
$h_sc_IndexedSeqView$Reverse.prototype = $p;
$p.d6 = (function() {
  return new $c_sc_IndexedSeqView$Reverse(this);
});
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var x = this.l();
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.q = (function() {
  return this.l();
});
$p.c = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), this);
});
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), this);
});
$p.bl = (function() {
  return "IndexedSeqView";
});
$p.aa = (function(f) {
  return $ct_sc_IndexedSeqView$Map__sc_IndexedSeqOps__F1__(new $c_sc_IndexedSeqView$Map(), this, f);
});
var $d_sc_IndexedSeqView$Reverse = new $TypeData().i($c_sc_IndexedSeqView$Reverse, "scala.collection.IndexedSeqView$Reverse", ({
  f7: 1,
  bV: 1,
  as: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  k: 1,
  a2: 1,
  n: 1,
  am: 1
}));
/** @constructor */
function $c_sci_AbstractSeq() {
}
$p = $c_sci_AbstractSeq.prototype = new $h_sc_AbstractSeq();
$p.constructor = $c_sci_AbstractSeq;
/** @constructor */
function $h_sci_AbstractSeq() {
}
$h_sci_AbstractSeq.prototype = $p;
/** @constructor */
function $c_scm_ArrayBufferView(underlying, mutationCount) {
  this.jX = null;
  this.iw = null;
  this.jX = underlying;
  this.iw = mutationCount;
}
$p = $c_scm_ArrayBufferView.prototype = new $h_sc_AbstractIndexedSeqView();
$p.constructor = $c_scm_ArrayBufferView;
/** @constructor */
function $h_scm_ArrayBufferView() {
}
$h_scm_ArrayBufferView.prototype = $p;
$p.r = (function(n) {
  return this.jX.r(n);
});
$p.l = (function() {
  return this.jX.bz;
});
$p.bK = (function() {
  return "ArrayBufferView";
});
$p.c = (function() {
  return new $c_scm_CheckedIndexedSeqView$CheckedIterator(this, this.iw);
});
$p.bj = (function() {
  return new $c_scm_CheckedIndexedSeqView$CheckedReverseIterator(this, this.iw);
});
$p.f2 = (function(f) {
  return new $c_scm_CheckedIndexedSeqView$Map(this, f, this.iw);
});
$p.aa = (function(f) {
  return this.f2(f);
});
var $d_scm_ArrayBufferView = new $TypeData().i($c_scm_ArrayBufferView, "scala.collection.mutable.ArrayBufferView", ({
  gL: 1,
  bH: 1,
  as: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  k: 1,
  a2: 1,
  n: 1,
  am: 1
}));
/** @constructor */
function $c_sci_AbstractMap() {
}
$p = $c_sci_AbstractMap.prototype = new $h_sc_AbstractMap();
$p.constructor = $c_sci_AbstractMap;
/** @constructor */
function $h_sci_AbstractMap() {
}
$h_sci_AbstractMap.prototype = $p;
$p.aH = (function() {
  return $m_sci_Iterable$();
});
$p.j1 = (function() {
  return $m_sci_Map$();
});
function $f_sci_IndexedSeq__canEqual__O__Z($thiz, that) {
  return ($is_sci_IndexedSeq(that) ? ($thiz.l() === that.l()) : true);
}
function $f_sci_IndexedSeq__sameElements__sc_IterableOnce__Z($thiz, o) {
  if ($is_sci_IndexedSeq(o)) {
    if (($thiz === o)) {
      return true;
    } else {
      var length = $thiz.l();
      var equal = (length === o.l());
      if (equal) {
        var index = 0;
        var a = $thiz.hC();
        var b = o.hC();
        var preferredLength = ((a < b) ? a : b);
        var hi = (length >> 31);
        var hi$1 = (preferredLength >> 31);
        var lo = (preferredLength << 1);
        var hi$2 = (((preferredLength >>> 31) | 0) | (hi$1 << 1));
        if (((hi === hi$2) ? ((length >>> 0) > (lo >>> 0)) : (hi > hi$2))) {
          var maxApplyCompare = preferredLength;
        } else {
          var maxApplyCompare = length;
        }
        while (((index < maxApplyCompare) && equal)) {
          equal = $m_sr_BoxesRunTime$().n($thiz.r(index), o.r(index));
          index = ((1 + index) | 0);
        }
        if (((index < length) && equal)) {
          var thisIt = $thiz.c().cE(index);
          var thatIt = o.c().cE(index);
          while ((equal && thisIt.k())) {
            equal = $m_sr_BoxesRunTime$().n(thisIt.f(), thatIt.f());
          }
        }
      }
      return equal;
    }
  } else {
    return $f_sc_SeqOps__sameElements__sc_IterableOnce__Z($thiz, o);
  }
}
function $is_sci_IndexedSeq(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.A)));
}
function $isArrayOf_sci_IndexedSeq(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.A)));
}
/** @constructor */
function $c_sci_Set$EmptySet$() {
}
$p = $c_sci_Set$EmptySet$.prototype = new $h_sci_AbstractSet();
$p.constructor = $c_sci_Set$EmptySet$;
/** @constructor */
function $h_sci_Set$EmptySet$() {
}
$h_sci_Set$EmptySet$.prototype = $p;
$p.M = (function() {
  return 0;
});
$p.g = (function() {
  return true;
});
$p.q = (function() {
  return 0;
});
$p.l9 = (function(that) {
  return true;
});
$p.bb = (function(elem) {
  return false;
});
$p.c = (function() {
  return $m_sc_Iterator$().G;
});
$p.dT = (function(elem) {
  return new $c_sci_Set$Set1(elem);
});
var $d_sci_Set$EmptySet$ = new $TypeData().i($c_sci_Set$EmptySet$, "scala.collection.immutable.Set$EmptySet$", ({
  gk: 1,
  av: 1,
  al: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  ao: 1,
  d: 1,
  aa: 1,
  q: 1,
  ay: 1,
  ax: 1,
  a: 1
}));
var $n_sci_Set$EmptySet$;
function $m_sci_Set$EmptySet$() {
  if ((!$n_sci_Set$EmptySet$)) {
    $n_sci_Set$EmptySet$ = new $c_sci_Set$EmptySet$();
  }
  return $n_sci_Set$EmptySet$;
}
/** @constructor */
function $c_sc_StringView(s) {
  this.fg = null;
  this.fg = s;
}
$p = $c_sc_StringView.prototype = new $h_sc_AbstractIndexedSeqView();
$p.constructor = $c_sc_StringView;
/** @constructor */
function $h_sc_StringView() {
}
$h_sc_StringView.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().dr(this, (-351294208), true);
});
$p.p = (function(x$0) {
  return ((this === x$0) || ((x$0 instanceof $c_sc_StringView) && (this.fg === x$0.fg)));
});
$p.bE = (function() {
  return 1;
});
$p.bG = (function() {
  return "StringView";
});
$p.bF = (function(n) {
  if ((n === 0)) {
    return this.fg;
  }
  throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
});
$p.l = (function() {
  return this.fg.length;
});
$p.t = (function() {
  return (("StringView(" + this.fg) + ")");
});
$p.r = (function(i) {
  return $bC(this.fg.charCodeAt(i));
});
function $isArrayOf_sc_StringView(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.bZ)));
}
var $d_sc_StringView = new $TypeData().i($c_sc_StringView, "scala.collection.StringView", ({
  bZ: 1,
  bH: 1,
  as: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  k: 1,
  a2: 1,
  n: 1,
  am: 1,
  d: 1,
  J: 1
}));
function $isArrayOf_sci_MapOps$LazyImmutableKeySet(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.ga)));
}
function $isArrayOf_sci_SeqMap$SeqMap1(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.gf)));
}
function $isArrayOf_sci_SeqMap$SeqMap2(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.gg)));
}
function $isArrayOf_sci_SeqMap$SeqMap3(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.gh)));
}
function $isArrayOf_sci_SeqMap$SeqMap4(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.gi)));
}
/** @constructor */
function $c_sci_Set$Set1(elem1) {
  this.fo = null;
  this.fo = elem1;
}
$p = $c_sci_Set$Set1.prototype = new $h_sci_AbstractSet();
$p.constructor = $c_sci_Set$Set1;
/** @constructor */
function $h_sci_Set$Set1() {
}
$h_sci_Set$Set1.prototype = $p;
$p.M = (function() {
  return 1;
});
$p.g = (function() {
  return false;
});
$p.q = (function() {
  return 1;
});
$p.bb = (function(elem) {
  return $m_sr_BoxesRunTime$().n(elem, this.fo);
});
$p.fE = (function(elem) {
  return (this.bb(elem) ? this : new $c_sci_Set$Set2(this.fo, elem));
});
$p.c = (function() {
  return new $c_sc_Iterator$$anon$20(this.fo);
});
$p.dN = (function(p) {
  return (!(!p.d(this.fo)));
});
$p.hG = (function(p) {
  return ((!(!p.d(this.fo))) ? new $c_s_Some(this.fo) : $m_s_None$());
});
$p.dT = (function(elem) {
  return this.fE(elem);
});
function $isArrayOf_sci_Set$Set1(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cn)));
}
var $d_sci_Set$Set1 = new $TypeData().i($c_sci_Set$Set1, "scala.collection.immutable.Set$Set1", ({
  cn: 1,
  av: 1,
  al: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  ao: 1,
  d: 1,
  aa: 1,
  q: 1,
  ay: 1,
  ax: 1,
  l: 1,
  a: 1
}));
/** @constructor */
function $c_sci_Set$Set2(elem1, elem2) {
  this.fp = null;
  this.fq = null;
  this.fp = elem1;
  this.fq = elem2;
}
$p = $c_sci_Set$Set2.prototype = new $h_sci_AbstractSet();
$p.constructor = $c_sci_Set$Set2;
/** @constructor */
function $h_sci_Set$Set2() {
}
$h_sci_Set$Set2.prototype = $p;
$p.M = (function() {
  return 2;
});
$p.g = (function() {
  return false;
});
$p.q = (function() {
  return 2;
});
$p.bb = (function(elem) {
  return ($m_sr_BoxesRunTime$().n(elem, this.fp) || $m_sr_BoxesRunTime$().n(elem, this.fq));
});
$p.fE = (function(elem) {
  return (this.bb(elem) ? this : new $c_sci_Set$Set3(this.fp, this.fq, elem));
});
$p.c = (function() {
  return new $c_sci_Set$Set2$$anon$1(this);
});
$p.rV = (function(i) {
  if ((i === 0)) {
    return this.fp;
  }
  if ((i === 1)) {
    return this.fq;
  }
  throw new $c_s_MatchError(i);
});
$p.dN = (function(p) {
  return ((!(!p.d(this.fp))) && (!(!p.d(this.fq))));
});
$p.hG = (function(p) {
  return ((!(!p.d(this.fp))) ? new $c_s_Some(this.fp) : ((!(!p.d(this.fq))) ? new $c_s_Some(this.fq) : $m_s_None$()));
});
$p.dT = (function(elem) {
  return this.fE(elem);
});
function $isArrayOf_sci_Set$Set2(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.co)));
}
var $d_sci_Set$Set2 = new $TypeData().i($c_sci_Set$Set2, "scala.collection.immutable.Set$Set2", ({
  co: 1,
  av: 1,
  al: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  ao: 1,
  d: 1,
  aa: 1,
  q: 1,
  ay: 1,
  ax: 1,
  l: 1,
  a: 1
}));
/** @constructor */
function $c_sci_Set$Set3(elem1, elem2, elem3) {
  this.fr = null;
  this.fs = null;
  this.ft = null;
  this.fr = elem1;
  this.fs = elem2;
  this.ft = elem3;
}
$p = $c_sci_Set$Set3.prototype = new $h_sci_AbstractSet();
$p.constructor = $c_sci_Set$Set3;
/** @constructor */
function $h_sci_Set$Set3() {
}
$h_sci_Set$Set3.prototype = $p;
$p.M = (function() {
  return 3;
});
$p.g = (function() {
  return false;
});
$p.q = (function() {
  return 3;
});
$p.bb = (function(elem) {
  return (($m_sr_BoxesRunTime$().n(elem, this.fr) || $m_sr_BoxesRunTime$().n(elem, this.fs)) || $m_sr_BoxesRunTime$().n(elem, this.ft));
});
$p.fE = (function(elem) {
  return (this.bb(elem) ? this : new $c_sci_Set$Set4(this.fr, this.fs, this.ft, elem));
});
$p.c = (function() {
  return new $c_sci_Set$Set3$$anon$2(this);
});
$p.rW = (function(i) {
  switch (i) {
    case 0: {
      return this.fr;
      break;
    }
    case 1: {
      return this.fs;
      break;
    }
    case 2: {
      return this.ft;
      break;
    }
    default: {
      throw new $c_s_MatchError(i);
    }
  }
});
$p.dN = (function(p) {
  return (((!(!p.d(this.fr))) && (!(!p.d(this.fs)))) && (!(!p.d(this.ft))));
});
$p.hG = (function(p) {
  return ((!(!p.d(this.fr))) ? new $c_s_Some(this.fr) : ((!(!p.d(this.fs))) ? new $c_s_Some(this.fs) : ((!(!p.d(this.ft))) ? new $c_s_Some(this.ft) : $m_s_None$())));
});
$p.dT = (function(elem) {
  return this.fE(elem);
});
function $isArrayOf_sci_Set$Set3(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cp)));
}
var $d_sci_Set$Set3 = new $TypeData().i($c_sci_Set$Set3, "scala.collection.immutable.Set$Set3", ({
  cp: 1,
  av: 1,
  al: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  ao: 1,
  d: 1,
  aa: 1,
  q: 1,
  ay: 1,
  ax: 1,
  l: 1,
  a: 1
}));
/** @constructor */
function $c_sci_Set$Set4(elem1, elem2, elem3, elem4) {
  this.eJ = null;
  this.eK = null;
  this.eL = null;
  this.eM = null;
  this.eJ = elem1;
  this.eK = elem2;
  this.eL = elem3;
  this.eM = elem4;
}
$p = $c_sci_Set$Set4.prototype = new $h_sci_AbstractSet();
$p.constructor = $c_sci_Set$Set4;
/** @constructor */
function $h_sci_Set$Set4() {
}
$h_sci_Set$Set4.prototype = $p;
$p.M = (function() {
  return 4;
});
$p.g = (function() {
  return false;
});
$p.q = (function() {
  return 4;
});
$p.bb = (function(elem) {
  return ((($m_sr_BoxesRunTime$().n(elem, this.eJ) || $m_sr_BoxesRunTime$().n(elem, this.eK)) || $m_sr_BoxesRunTime$().n(elem, this.eL)) || $m_sr_BoxesRunTime$().n(elem, this.eM));
});
$p.fE = (function(elem) {
  return (this.bb(elem) ? this : $m_sci_HashSet$().iu.gt(this.eJ).gt(this.eK).gt(this.eL).gt(this.eM).gt(elem));
});
$p.c = (function() {
  return new $c_sci_Set$Set4$$anon$3(this);
});
$p.rX = (function(i) {
  switch (i) {
    case 0: {
      return this.eJ;
      break;
    }
    case 1: {
      return this.eK;
      break;
    }
    case 2: {
      return this.eL;
      break;
    }
    case 3: {
      return this.eM;
      break;
    }
    default: {
      throw new $c_s_MatchError(i);
    }
  }
});
$p.dN = (function(p) {
  return ((((!(!p.d(this.eJ))) && (!(!p.d(this.eK)))) && (!(!p.d(this.eL)))) && (!(!p.d(this.eM))));
});
$p.hG = (function(p) {
  return ((!(!p.d(this.eJ))) ? new $c_s_Some(this.eJ) : ((!(!p.d(this.eK))) ? new $c_s_Some(this.eK) : ((!(!p.d(this.eL))) ? new $c_s_Some(this.eL) : ((!(!p.d(this.eM))) ? new $c_s_Some(this.eM) : $m_s_None$()))));
});
$p.q9 = (function(builder) {
  return builder.aq(this.eJ).aq(this.eK).aq(this.eL).aq(this.eM);
});
$p.dT = (function(elem) {
  return this.fE(elem);
});
function $isArrayOf_sci_Set$Set4(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cq)));
}
var $d_sci_Set$Set4 = new $TypeData().i($c_sci_Set$Set4, "scala.collection.immutable.Set$Set4", ({
  cq: 1,
  av: 1,
  al: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  ao: 1,
  d: 1,
  aa: 1,
  q: 1,
  ay: 1,
  ax: 1,
  l: 1,
  a: 1
}));
/** @constructor */
function $c_scm_AbstractSeq() {
}
$p = $c_scm_AbstractSeq.prototype = new $h_sc_AbstractSeq();
$p.constructor = $c_scm_AbstractSeq;
/** @constructor */
function $h_scm_AbstractSeq() {
}
$h_scm_AbstractSeq.prototype = $p;
/** @constructor */
function $c_scm_CheckedIndexedSeqView$Map(underlying, f, mutationCount) {
  this.fh = null;
  this.h1 = null;
  this.gY = null;
  this.im = null;
  this.ix = null;
  this.ix = mutationCount;
  $ct_sc_IndexedSeqView$Map__sc_IndexedSeqOps__F1__(this, underlying, f);
}
$p = $c_scm_CheckedIndexedSeqView$Map.prototype = new $h_sc_IndexedSeqView$Map();
$p.constructor = $c_scm_CheckedIndexedSeqView$Map;
/** @constructor */
function $h_scm_CheckedIndexedSeqView$Map() {
}
$h_scm_CheckedIndexedSeqView$Map.prototype = $p;
$p.c = (function() {
  return $f_scm_CheckedIndexedSeqView__iterator__sc_Iterator(this);
});
$p.bj = (function() {
  return $f_scm_CheckedIndexedSeqView__reverseIterator__sc_Iterator(this);
});
$p.f2 = (function(f) {
  return new $c_scm_CheckedIndexedSeqView$Map(this, f, this.ix);
});
$p.oA = (function() {
  return this.ix;
});
$p.aa = (function(f) {
  return new $c_scm_CheckedIndexedSeqView$Map(this, f, this.ix);
});
var $d_scm_CheckedIndexedSeqView$Map = new $TypeData().i($c_scm_CheckedIndexedSeqView$Map, "scala.collection.mutable.CheckedIndexedSeqView$Map", ({
  gV: 1,
  bO: 1,
  bU: 1,
  aY: 1,
  L: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  a: 1,
  C: 1,
  k: 1,
  a2: 1,
  n: 1,
  am: 1,
  gS: 1
}));
function $isArrayOf_sci_HashMap$HashKeySet(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.fE)));
}
/** @constructor */
function $c_sci_Map$EmptyMap$() {
}
$p = $c_sci_Map$EmptyMap$.prototype = new $h_sci_AbstractMap();
$p.constructor = $c_sci_Map$EmptyMap$;
/** @constructor */
function $h_sci_Map$EmptyMap$() {
}
$h_sci_Map$EmptyMap$.prototype = $p;
$p.M = (function() {
  return 0;
});
$p.q = (function() {
  return 0;
});
$p.g = (function() {
  return true;
});
$p.iQ = (function(key) {
  throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), ("key not found: " + key));
});
$p.bb = (function(key) {
  return false;
});
$p.cn = (function(key) {
  return $m_s_None$();
});
$p.eo = (function(key, default$1) {
  return default$1.a2();
});
$p.c = (function() {
  return $m_sc_Iterator$().G;
});
$p.d = (function(key) {
  this.iQ(key);
});
$p.fO = (function(key, value) {
  return new $c_sci_Map$Map1(key, value);
});
var $d_sci_Map$EmptyMap$ = new $TypeData().i($c_sci_Map$EmptyMap$, "scala.collection.immutable.Map$EmptyMap$", ({
  fZ: 1,
  au: 1,
  ak: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  a9: 1,
  an: 1,
  d: 1,
  a8: 1,
  q: 1,
  aw: 1,
  af: 1,
  a: 1
}));
var $n_sci_Map$EmptyMap$;
function $m_sci_Map$EmptyMap$() {
  if ((!$n_sci_Map$EmptyMap$)) {
    $n_sci_Map$EmptyMap$ = new $c_sci_Map$EmptyMap$();
  }
  return $n_sci_Map$EmptyMap$;
}
/** @constructor */
function $c_sci_Map$Map1(key1, value1) {
  this.df = null;
  this.eC = null;
  this.df = key1;
  this.eC = value1;
}
$p = $c_sci_Map$Map1.prototype = new $h_sci_AbstractMap();
$p.constructor = $c_sci_Map$Map1;
/** @constructor */
function $h_sci_Map$Map1() {
}
$h_sci_Map$Map1.prototype = $p;
$p.aa = (function(f) {
  return $f_sc_StrictOptimizedIterableOps__map__F1__O(this, f);
});
$p.M = (function() {
  return 1;
});
$p.q = (function() {
  return 1;
});
$p.g = (function() {
  return false;
});
$p.d = (function(key) {
  if ($m_sr_BoxesRunTime$().n(key, this.df)) {
    return this.eC;
  } else {
    throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), ("key not found: " + key));
  }
});
$p.bb = (function(key) {
  return $m_sr_BoxesRunTime$().n(key, this.df);
});
$p.cn = (function(key) {
  return ($m_sr_BoxesRunTime$().n(key, this.df) ? new $c_s_Some(this.eC) : $m_s_None$());
});
$p.eo = (function(key, default$1) {
  return ($m_sr_BoxesRunTime$().n(key, this.df) ? this.eC : default$1.a2());
});
$p.c = (function() {
  return new $c_sc_Iterator$$anon$20($ct_T2__O__O__(new $c_T2(), this.df, this.eC));
});
$p.fN = (function(key, value) {
  return ($m_sr_BoxesRunTime$().n(key, this.df) ? new $c_sci_Map$Map1(this.df, value) : new $c_sci_Map$Map2(this.df, this.eC, key, value));
});
$p.dN = (function(p) {
  return (!(!p.d($ct_T2__O__O__(new $c_T2(), this.df, this.eC))));
});
$p.u = (function() {
  var a = 0;
  var b = 0;
  var c = 1;
  var h = $m_s_util_hashing_MurmurHash3$().d7(this.df, this.eC);
  a = ((a + h) | 0);
  b = (b ^ h);
  c = Math.imul(c, (1 | h));
  h = $m_s_util_hashing_MurmurHash3$().el;
  h = $m_s_util_hashing_MurmurHash3$().x(h, a);
  h = $m_s_util_hashing_MurmurHash3$().x(h, b);
  h = $m_s_util_hashing_MurmurHash3$().eq(h, c);
  return $m_s_util_hashing_MurmurHash3$().bd(h, 1);
});
$p.fO = (function(key, value) {
  return this.fN(key, value);
});
function $isArrayOf_sci_Map$Map1(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.ci)));
}
var $d_sci_Map$Map1 = new $TypeData().i($c_sci_Map$Map1, "scala.collection.immutable.Map$Map1", ({
  ci: 1,
  au: 1,
  ak: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  a9: 1,
  an: 1,
  d: 1,
  a8: 1,
  q: 1,
  aw: 1,
  af: 1,
  l: 1,
  a: 1
}));
/** @constructor */
function $c_sci_Map$Map2(key1, value1, key2, value2) {
  this.cS = null;
  this.e8 = null;
  this.cT = null;
  this.e9 = null;
  this.cS = key1;
  this.e8 = value1;
  this.cT = key2;
  this.e9 = value2;
}
$p = $c_sci_Map$Map2.prototype = new $h_sci_AbstractMap();
$p.constructor = $c_sci_Map$Map2;
/** @constructor */
function $h_sci_Map$Map2() {
}
$h_sci_Map$Map2.prototype = $p;
$p.aa = (function(f) {
  return $f_sc_StrictOptimizedIterableOps__map__F1__O(this, f);
});
$p.M = (function() {
  return 2;
});
$p.q = (function() {
  return 2;
});
$p.g = (function() {
  return false;
});
$p.d = (function(key) {
  if ($m_sr_BoxesRunTime$().n(key, this.cS)) {
    return this.e8;
  } else if ($m_sr_BoxesRunTime$().n(key, this.cT)) {
    return this.e9;
  } else {
    throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), ("key not found: " + key));
  }
});
$p.bb = (function(key) {
  return ($m_sr_BoxesRunTime$().n(key, this.cS) || $m_sr_BoxesRunTime$().n(key, this.cT));
});
$p.cn = (function(key) {
  return ($m_sr_BoxesRunTime$().n(key, this.cS) ? new $c_s_Some(this.e8) : ($m_sr_BoxesRunTime$().n(key, this.cT) ? new $c_s_Some(this.e9) : $m_s_None$()));
});
$p.eo = (function(key, default$1) {
  return ($m_sr_BoxesRunTime$().n(key, this.cS) ? this.e8 : ($m_sr_BoxesRunTime$().n(key, this.cT) ? this.e9 : default$1.a2()));
});
$p.c = (function() {
  return new $c_sci_Map$Map2$$anon$1(this);
});
$p.fN = (function(key, value) {
  return ($m_sr_BoxesRunTime$().n(key, this.cS) ? new $c_sci_Map$Map2(this.cS, value, this.cT, this.e9) : ($m_sr_BoxesRunTime$().n(key, this.cT) ? new $c_sci_Map$Map2(this.cS, this.e8, this.cT, value) : new $c_sci_Map$Map3(this.cS, this.e8, this.cT, this.e9, key, value)));
});
$p.dN = (function(p) {
  return ((!(!p.d($ct_T2__O__O__(new $c_T2(), this.cS, this.e8)))) && (!(!p.d($ct_T2__O__O__(new $c_T2(), this.cT, this.e9)))));
});
$p.u = (function() {
  var a = 0;
  var b = 0;
  var c = 1;
  var h = $m_s_util_hashing_MurmurHash3$().d7(this.cS, this.e8);
  a = ((a + h) | 0);
  b = (b ^ h);
  c = Math.imul(c, (1 | h));
  h = $m_s_util_hashing_MurmurHash3$().d7(this.cT, this.e9);
  a = ((a + h) | 0);
  b = (b ^ h);
  c = Math.imul(c, (1 | h));
  h = $m_s_util_hashing_MurmurHash3$().el;
  h = $m_s_util_hashing_MurmurHash3$().x(h, a);
  h = $m_s_util_hashing_MurmurHash3$().x(h, b);
  h = $m_s_util_hashing_MurmurHash3$().eq(h, c);
  return $m_s_util_hashing_MurmurHash3$().bd(h, 2);
});
$p.fO = (function(key, value) {
  return this.fN(key, value);
});
function $isArrayOf_sci_Map$Map2(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cj)));
}
var $d_sci_Map$Map2 = new $TypeData().i($c_sci_Map$Map2, "scala.collection.immutable.Map$Map2", ({
  cj: 1,
  au: 1,
  ak: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  a9: 1,
  an: 1,
  d: 1,
  a8: 1,
  q: 1,
  aw: 1,
  af: 1,
  l: 1,
  a: 1
}));
/** @constructor */
function $c_sci_Map$Map3(key1, value1, key2, value2, key3, value3) {
  this.cy = null;
  this.dD = null;
  this.cz = null;
  this.dE = null;
  this.cA = null;
  this.dF = null;
  this.cy = key1;
  this.dD = value1;
  this.cz = key2;
  this.dE = value2;
  this.cA = key3;
  this.dF = value3;
}
$p = $c_sci_Map$Map3.prototype = new $h_sci_AbstractMap();
$p.constructor = $c_sci_Map$Map3;
/** @constructor */
function $h_sci_Map$Map3() {
}
$h_sci_Map$Map3.prototype = $p;
$p.aa = (function(f) {
  return $f_sc_StrictOptimizedIterableOps__map__F1__O(this, f);
});
$p.M = (function() {
  return 3;
});
$p.q = (function() {
  return 3;
});
$p.g = (function() {
  return false;
});
$p.d = (function(key) {
  if ($m_sr_BoxesRunTime$().n(key, this.cy)) {
    return this.dD;
  } else if ($m_sr_BoxesRunTime$().n(key, this.cz)) {
    return this.dE;
  } else if ($m_sr_BoxesRunTime$().n(key, this.cA)) {
    return this.dF;
  } else {
    throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), ("key not found: " + key));
  }
});
$p.bb = (function(key) {
  return (($m_sr_BoxesRunTime$().n(key, this.cy) || $m_sr_BoxesRunTime$().n(key, this.cz)) || $m_sr_BoxesRunTime$().n(key, this.cA));
});
$p.cn = (function(key) {
  return ($m_sr_BoxesRunTime$().n(key, this.cy) ? new $c_s_Some(this.dD) : ($m_sr_BoxesRunTime$().n(key, this.cz) ? new $c_s_Some(this.dE) : ($m_sr_BoxesRunTime$().n(key, this.cA) ? new $c_s_Some(this.dF) : $m_s_None$())));
});
$p.eo = (function(key, default$1) {
  return ($m_sr_BoxesRunTime$().n(key, this.cy) ? this.dD : ($m_sr_BoxesRunTime$().n(key, this.cz) ? this.dE : ($m_sr_BoxesRunTime$().n(key, this.cA) ? this.dF : default$1.a2())));
});
$p.c = (function() {
  return new $c_sci_Map$Map3$$anon$4(this);
});
$p.fN = (function(key, value) {
  return ($m_sr_BoxesRunTime$().n(key, this.cy) ? new $c_sci_Map$Map3(this.cy, value, this.cz, this.dE, this.cA, this.dF) : ($m_sr_BoxesRunTime$().n(key, this.cz) ? new $c_sci_Map$Map3(this.cy, this.dD, this.cz, value, this.cA, this.dF) : ($m_sr_BoxesRunTime$().n(key, this.cA) ? new $c_sci_Map$Map3(this.cy, this.dD, this.cz, this.dE, this.cA, value) : new $c_sci_Map$Map4(this.cy, this.dD, this.cz, this.dE, this.cA, this.dF, key, value))));
});
$p.dN = (function(p) {
  return (((!(!p.d($ct_T2__O__O__(new $c_T2(), this.cy, this.dD)))) && (!(!p.d($ct_T2__O__O__(new $c_T2(), this.cz, this.dE))))) && (!(!p.d($ct_T2__O__O__(new $c_T2(), this.cA, this.dF)))));
});
$p.u = (function() {
  var a = 0;
  var b = 0;
  var c = 1;
  var h = $m_s_util_hashing_MurmurHash3$().d7(this.cy, this.dD);
  a = ((a + h) | 0);
  b = (b ^ h);
  c = Math.imul(c, (1 | h));
  h = $m_s_util_hashing_MurmurHash3$().d7(this.cz, this.dE);
  a = ((a + h) | 0);
  b = (b ^ h);
  c = Math.imul(c, (1 | h));
  h = $m_s_util_hashing_MurmurHash3$().d7(this.cA, this.dF);
  a = ((a + h) | 0);
  b = (b ^ h);
  c = Math.imul(c, (1 | h));
  h = $m_s_util_hashing_MurmurHash3$().el;
  h = $m_s_util_hashing_MurmurHash3$().x(h, a);
  h = $m_s_util_hashing_MurmurHash3$().x(h, b);
  h = $m_s_util_hashing_MurmurHash3$().eq(h, c);
  return $m_s_util_hashing_MurmurHash3$().bd(h, 3);
});
$p.fO = (function(key, value) {
  return this.fN(key, value);
});
function $isArrayOf_sci_Map$Map3(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.ck)));
}
var $d_sci_Map$Map3 = new $TypeData().i($c_sci_Map$Map3, "scala.collection.immutable.Map$Map3", ({
  ck: 1,
  au: 1,
  ak: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  a9: 1,
  an: 1,
  d: 1,
  a8: 1,
  q: 1,
  aw: 1,
  af: 1,
  l: 1,
  a: 1
}));
/** @constructor */
function $c_sci_Map$Map4(key1, value1, key2, value2, key3, value3, key4, value4) {
  this.c5 = null;
  this.cU = null;
  this.c6 = null;
  this.cV = null;
  this.c7 = null;
  this.cW = null;
  this.c8 = null;
  this.cX = null;
  this.c5 = key1;
  this.cU = value1;
  this.c6 = key2;
  this.cV = value2;
  this.c7 = key3;
  this.cW = value3;
  this.c8 = key4;
  this.cX = value4;
}
$p = $c_sci_Map$Map4.prototype = new $h_sci_AbstractMap();
$p.constructor = $c_sci_Map$Map4;
/** @constructor */
function $h_sci_Map$Map4() {
}
$h_sci_Map$Map4.prototype = $p;
$p.aa = (function(f) {
  return $f_sc_StrictOptimizedIterableOps__map__F1__O(this, f);
});
$p.M = (function() {
  return 4;
});
$p.q = (function() {
  return 4;
});
$p.g = (function() {
  return false;
});
$p.d = (function(key) {
  if ($m_sr_BoxesRunTime$().n(key, this.c5)) {
    return this.cU;
  } else if ($m_sr_BoxesRunTime$().n(key, this.c6)) {
    return this.cV;
  } else if ($m_sr_BoxesRunTime$().n(key, this.c7)) {
    return this.cW;
  } else if ($m_sr_BoxesRunTime$().n(key, this.c8)) {
    return this.cX;
  } else {
    throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), ("key not found: " + key));
  }
});
$p.bb = (function(key) {
  return ((($m_sr_BoxesRunTime$().n(key, this.c5) || $m_sr_BoxesRunTime$().n(key, this.c6)) || $m_sr_BoxesRunTime$().n(key, this.c7)) || $m_sr_BoxesRunTime$().n(key, this.c8));
});
$p.cn = (function(key) {
  return ($m_sr_BoxesRunTime$().n(key, this.c5) ? new $c_s_Some(this.cU) : ($m_sr_BoxesRunTime$().n(key, this.c6) ? new $c_s_Some(this.cV) : ($m_sr_BoxesRunTime$().n(key, this.c7) ? new $c_s_Some(this.cW) : ($m_sr_BoxesRunTime$().n(key, this.c8) ? new $c_s_Some(this.cX) : $m_s_None$()))));
});
$p.eo = (function(key, default$1) {
  return ($m_sr_BoxesRunTime$().n(key, this.c5) ? this.cU : ($m_sr_BoxesRunTime$().n(key, this.c6) ? this.cV : ($m_sr_BoxesRunTime$().n(key, this.c7) ? this.cW : ($m_sr_BoxesRunTime$().n(key, this.c8) ? this.cX : default$1.a2()))));
});
$p.c = (function() {
  return new $c_sci_Map$Map4$$anon$7(this);
});
$p.fN = (function(key, value) {
  return ($m_sr_BoxesRunTime$().n(key, this.c5) ? new $c_sci_Map$Map4(this.c5, value, this.c6, this.cV, this.c7, this.cW, this.c8, this.cX) : ($m_sr_BoxesRunTime$().n(key, this.c6) ? new $c_sci_Map$Map4(this.c5, this.cU, this.c6, value, this.c7, this.cW, this.c8, this.cX) : ($m_sr_BoxesRunTime$().n(key, this.c7) ? new $c_sci_Map$Map4(this.c5, this.cU, this.c6, this.cV, this.c7, value, this.c8, this.cX) : ($m_sr_BoxesRunTime$().n(key, this.c8) ? new $c_sci_Map$Map4(this.c5, this.cU, this.c6, this.cV, this.c7, this.cW, this.c8, value) : $m_sci_HashMap$().h4.fM(this.c5, this.cU).fM(this.c6, this.cV).fM(this.c7, this.cW).fM(this.c8, this.cX).fM(key, value)))));
});
$p.dN = (function(p) {
  return ((((!(!p.d($ct_T2__O__O__(new $c_T2(), this.c5, this.cU)))) && (!(!p.d($ct_T2__O__O__(new $c_T2(), this.c6, this.cV))))) && (!(!p.d($ct_T2__O__O__(new $c_T2(), this.c7, this.cW))))) && (!(!p.d($ct_T2__O__O__(new $c_T2(), this.c8, this.cX)))));
});
$p.q8 = (function(builder) {
  return builder.fA(this.c5, this.cU).fA(this.c6, this.cV).fA(this.c7, this.cW).fA(this.c8, this.cX);
});
$p.u = (function() {
  var a = 0;
  var b = 0;
  var c = 1;
  var h = $m_s_util_hashing_MurmurHash3$().d7(this.c5, this.cU);
  a = ((a + h) | 0);
  b = (b ^ h);
  c = Math.imul(c, (1 | h));
  h = $m_s_util_hashing_MurmurHash3$().d7(this.c6, this.cV);
  a = ((a + h) | 0);
  b = (b ^ h);
  c = Math.imul(c, (1 | h));
  h = $m_s_util_hashing_MurmurHash3$().d7(this.c7, this.cW);
  a = ((a + h) | 0);
  b = (b ^ h);
  c = Math.imul(c, (1 | h));
  h = $m_s_util_hashing_MurmurHash3$().d7(this.c8, this.cX);
  a = ((a + h) | 0);
  b = (b ^ h);
  c = Math.imul(c, (1 | h));
  h = $m_s_util_hashing_MurmurHash3$().el;
  h = $m_s_util_hashing_MurmurHash3$().x(h, a);
  h = $m_s_util_hashing_MurmurHash3$().x(h, b);
  h = $m_s_util_hashing_MurmurHash3$().eq(h, c);
  return $m_s_util_hashing_MurmurHash3$().bd(h, 4);
});
$p.fO = (function(key, value) {
  return this.fN(key, value);
});
function $isArrayOf_sci_Map$Map4(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cl)));
}
var $d_sci_Map$Map4 = new $TypeData().i($c_sci_Map$Map4, "scala.collection.immutable.Map$Map4", ({
  cl: 1,
  au: 1,
  ak: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  a9: 1,
  an: 1,
  d: 1,
  a8: 1,
  q: 1,
  aw: 1,
  af: 1,
  l: 1,
  a: 1
}));
function $is_scm_Map(obj) {
  return (!(!((obj && obj.$classData) && obj.$classData.n.b7)));
}
function $isArrayOf_scm_Map(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.b7)));
}
function $p_sci_HashSet__newHashSetOrThis__sci_BitmapIndexedSetNode__sci_HashSet($thiz, newRootNode) {
  return (($thiz.c4 === newRootNode) ? $thiz : new $c_sci_HashSet(newRootNode));
}
/** @constructor */
function $c_sci_HashSet(rootNode) {
  this.c4 = null;
  this.c4 = rootNode;
}
$p = $c_sci_HashSet.prototype = new $h_sci_AbstractSet();
$p.constructor = $c_sci_HashSet;
/** @constructor */
function $h_sci_HashSet() {
}
$h_sci_HashSet.prototype = $p;
$p.aH = (function() {
  return $m_sci_HashSet$();
});
$p.q = (function() {
  return this.c4.bt;
});
$p.M = (function() {
  return this.c4.bt;
});
$p.g = (function() {
  return (this.c4.bt === 0);
});
$p.c = (function() {
  return (this.g() ? $m_sc_Iterator$().G : new $c_sci_SetIterator(this.c4));
});
$p.bb = (function(element) {
  var elementUnimprovedHash = $m_sr_Statics$().y(element);
  var elementHash = $m_sc_Hashing$().bD(elementUnimprovedHash);
  return this.c4.hE(element, elementUnimprovedHash, elementHash, 0);
});
$p.gt = (function(element) {
  var elementUnimprovedHash = $m_sr_Statics$().y(element);
  var elementHash = $m_sc_Hashing$().bD(elementUnimprovedHash);
  return $p_sci_HashSet__newHashSetOrThis__sci_BitmapIndexedSetNode__sci_HashSet(this, this.c4.p3(element, elementUnimprovedHash, elementHash, 0));
});
$p.l9 = (function(that) {
  return (this.g() || ((!that.g()) && ((that instanceof $c_sci_HashSet) ? this.c4.la(that.c4, 0) : $f_sc_IterableOnceOps__forall__F1__Z(this, that))));
});
$p.p = (function(that) {
  if ((that instanceof $c_sci_HashSet)) {
    if ((this === that)) {
      return true;
    } else {
      var x = this.c4;
      var x$2 = that.c4;
      return ((x === null) ? (x$2 === null) : x.p(x$2));
    }
  } else {
    return $f_sc_Set__equals__O__Z(this, that);
  }
});
$p.bK = (function() {
  return "HashSet";
});
$p.u = (function() {
  var it = new $c_sci_SetHashIterator(this.c4);
  return $m_s_util_hashing_MurmurHash3$().gA(it, $m_s_util_hashing_MurmurHash3$().iI);
});
$p.dT = (function(elem) {
  return this.gt(elem);
});
function $isArrayOf_sci_HashSet(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cg)));
}
var $d_sci_HashSet = new $TypeData().i($c_sci_HashSet, "scala.collection.immutable.HashSet", ({
  cg: 1,
  av: 1,
  al: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  ao: 1,
  d: 1,
  aa: 1,
  q: 1,
  ay: 1,
  ax: 1,
  l: 1,
  fs: 1,
  gt: 1,
  a: 1,
  X: 1
}));
function $isArrayOf_sci_ListSet(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.fX)));
}
/** @constructor */
function $c_scm_AbstractSet() {
}
$p = $c_scm_AbstractSet.prototype = new $h_sc_AbstractSet();
$p.constructor = $c_scm_AbstractSet;
/** @constructor */
function $h_scm_AbstractSet() {
}
$h_scm_AbstractSet.prototype = $p;
$p.aU = (function() {
  return this;
});
function $ct_sci_LazyList__O__($thiz, lazyState) {
  $thiz.bO = ((lazyState === $m_sci_LazyList$EmptyMarker$()) ? null : $m_sci_LazyList$Uninitialized$());
  $thiz.dC = ((lazyState === $m_sci_LazyList$EmptyMarker$()) ? null : lazyState);
  return $thiz;
}
function $ct_sci_LazyList__O__sci_LazyList__($thiz, head, tail) {
  $ct_sci_LazyList__O__($thiz, $m_sci_LazyList$EmptyMarker$());
  $thiz.bO = head;
  $thiz.dC = tail;
  return $thiz;
}
function $p_sci_LazyList__initState__V($thiz) {
  if (($thiz.bO === $m_sci_LazyList$Uninitialized$())) {
    if (($thiz.dC === $m_sci_LazyList$MidEvaluation$())) {
      throw $ct_jl_RuntimeException__T__(new $c_jl_RuntimeException(), "LazyList evaluation depends on its own result (self-reference); see docs for more info");
    }
    var fun = $thiz.dC;
    $thiz.dC = $m_sci_LazyList$MidEvaluation$();
    try {
      var l = $p_sci_LazyList__evaluated__sci_LazyList(fun.a2());
    } finally {
      $thiz.dC = fun;
    }
    $thiz.dC = l.dC;
    $thiz.bO = l.bO;
  }
}
function $p_sci_LazyList__evaluated__sci_LazyList($thiz) {
  while (true) {
    if (($thiz.bO !== $m_sci_LazyList$Uninitialized$())) {
      return (($thiz.dC === null) ? $m_sci_LazyList$().ah : $thiz);
    } else {
      $p_sci_LazyList__initState__V($thiz);
    }
  }
}
function $p_sci_LazyList__mapImpl__F1__sci_LazyList($thiz, f) {
  $m_sci_LazyList$();
  return $ct_sci_LazyList__O__(new $c_sci_LazyList(), new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => (($p_sci_LazyList__evaluated__sci_LazyList($thiz) === $m_sci_LazyList$().ah) ? $m_sci_LazyList$().ah : ($m_sci_LazyList$(), $ct_sci_LazyList__O__sci_LazyList__(new $c_sci_LazyList(), f.d($thiz.m()), $p_sci_LazyList__mapImpl__F1__sci_LazyList($thiz.bI(), f)))))));
}
function $p_sci_LazyList__addStringNoForce__jl_StringBuilder__T__T__T__jl_StringBuilder($thiz, b, start, sep, end) {
  b.w = (("" + b.w) + start);
  if (($thiz.bO === $m_sci_LazyList$Uninitialized$())) {
    b.w = (b.w + "<not computed>");
  } else if (($p_sci_LazyList__evaluated__sci_LazyList($thiz) !== $m_sci_LazyList$().ah)) {
    var obj = $thiz.m();
    b.w = (("" + b.w) + obj);
    var cursor = $thiz;
    var scout = $thiz.bI();
    if ((cursor !== scout)) {
      cursor = scout;
      var this$1 = scout;
      if (((this$1.bO !== $m_sci_LazyList$Uninitialized$()) && ($p_sci_LazyList__evaluated__sci_LazyList(this$1) !== $m_sci_LazyList$().ah))) {
        scout = scout.bI();
        while (true) {
          if ((cursor !== scout)) {
            var this$2 = scout;
            var $x_1 = ((this$2.bO !== $m_sci_LazyList$Uninitialized$()) && ($p_sci_LazyList__evaluated__sci_LazyList(this$2) !== $m_sci_LazyList$().ah));
          } else {
            var $x_1 = false;
          }
          if ($x_1) {
            var c = cursor;
            b.w = (("" + b.w) + sep);
            var obj$1 = c.m();
            b.w = (("" + b.w) + obj$1);
            cursor = cursor.bI();
            scout = scout.bI();
            var this$3 = scout;
            if (((this$3.bO !== $m_sci_LazyList$Uninitialized$()) && ($p_sci_LazyList__evaluated__sci_LazyList(this$3) !== $m_sci_LazyList$().ah))) {
              scout = scout.bI();
            }
          } else {
            break;
          }
        }
      }
    }
    var this$4 = scout;
    if ((!((this$4.bO !== $m_sci_LazyList$Uninitialized$()) && ($p_sci_LazyList__evaluated__sci_LazyList(this$4) !== $m_sci_LazyList$().ah)))) {
      while ((cursor !== scout)) {
        var c$1 = cursor;
        b.w = (("" + b.w) + sep);
        var obj$2 = c$1.m();
        b.w = (("" + b.w) + obj$2);
        cursor = cursor.bI();
      }
      if ((!(cursor.bO !== $m_sci_LazyList$Uninitialized$()))) {
        b.w = (("" + b.w) + sep);
        b.w = (b.w + "<not computed>");
      }
    } else {
      if ((cursor !== $thiz)) {
        var runner = $thiz;
        while ((runner !== scout)) {
          runner = runner.bI();
          scout = scout.bI();
        }
        while (true) {
          var ct = cursor.bI();
          if ((ct !== scout)) {
            var c$2 = cursor;
            b.w = (("" + b.w) + sep);
            var obj$3 = c$2.m();
            b.w = (("" + b.w) + obj$3);
          }
          cursor = ct;
          if ((cursor !== scout)) {
          } else {
            break;
          }
        }
      }
      b.w = (("" + b.w) + sep);
      b.w = (b.w + "<cycle>");
    }
  }
  b.w = (("" + b.w) + end);
  return b;
}
/** @constructor */
function $c_sci_LazyList() {
  this.bO = null;
  this.dC = null;
}
$p = $c_sci_LazyList.prototype = new $h_sci_AbstractSeq();
$p.constructor = $c_sci_LazyList;
/** @constructor */
function $h_sci_LazyList() {
}
$h_sci_LazyList.prototype = $p;
$p.l = (function() {
  return $f_sc_LinearSeqOps__length__I(this);
});
$p.aI = (function(len) {
  return $f_sc_LinearSeqOps__lengthCompare__I__I(this, len);
});
$p.kS = (function(x) {
  return $f_sc_LinearSeqOps__isDefinedAt__I__Z(this, x);
});
$p.r = (function(n) {
  return $f_sc_LinearSeqOps__apply__I__O(this, n);
});
$p.fC = (function(p) {
  return $f_sc_LinearSeqOps__exists__F1__Z(this, p);
});
$p.f3 = (function(that) {
  return $f_sc_LinearSeqOps__sameElements__sc_IterableOnce__Z(this, that);
});
$p.dU = (function(p, from) {
  return $f_sc_LinearSeqOps__indexWhere__F1__I__I(this, p, from);
});
$p.bl = (function() {
  return "LinearSeq";
});
$p.g = (function() {
  return ($p_sci_LazyList__evaluated__sci_LazyList(this) === $m_sci_LazyList$().ah);
});
$p.q = (function() {
  return (((this.bO !== $m_sci_LazyList$Uninitialized$()) && ($p_sci_LazyList__evaluated__sci_LazyList(this) === $m_sci_LazyList$().ah)) ? 0 : (-1));
});
$p.m = (function() {
  if (($p_sci_LazyList__evaluated__sci_LazyList(this) === $m_sci_LazyList$().ah)) {
    throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), "head of empty lazy list");
  } else {
    return this.bO;
  }
});
$p.bI = (function() {
  if (($p_sci_LazyList__evaluated__sci_LazyList(this) === $m_sci_LazyList$().ah)) {
    throw new $c_jl_UnsupportedOperationException("tail of empty lazy list");
  } else {
    return this.dC;
  }
});
$p.qO = (function() {
  var these = this;
  var those = this;
  if ((!($p_sci_LazyList__evaluated__sci_LazyList(these) === $m_sci_LazyList$().ah))) {
    these = these.bI();
  }
  while ((those !== these)) {
    if (($p_sci_LazyList__evaluated__sci_LazyList(these) === $m_sci_LazyList$().ah)) {
      return this;
    }
    these = these.bI();
    if (($p_sci_LazyList__evaluated__sci_LazyList(these) === $m_sci_LazyList$().ah)) {
      return this;
    }
    these = these.bI();
    if ((these === those)) {
      return this;
    }
    those = those.bI();
  }
  return this;
});
$p.c = (function() {
  return (((this.bO !== $m_sci_LazyList$Uninitialized$()) && ($p_sci_LazyList__evaluated__sci_LazyList(this) === $m_sci_LazyList$().ah)) ? $m_sc_Iterator$().G : new $c_sci_LazyList$LazyIterator(this));
});
$p.dO = (function(f) {
  var \u03b4this$tailLocal1 = this;
  while (true) {
    if ((!($p_sci_LazyList__evaluated__sci_LazyList(\u03b4this$tailLocal1) === $m_sci_LazyList$().ah))) {
      f.d(\u03b4this$tailLocal1.m());
      \u03b4this$tailLocal1 = \u03b4this$tailLocal1.bI();
    } else {
      return (void 0);
    }
  }
});
$p.d4 = (function(z, op) {
  var \u03b4this$tailLocal2 = this;
  var z$tailLocal1 = z;
  while (true) {
    if (($p_sci_LazyList__evaluated__sci_LazyList(\u03b4this$tailLocal2) === $m_sci_LazyList$().ah)) {
      return z$tailLocal1;
    } else {
      var \u03b4this$tailLocal2$tmp1 = \u03b4this$tailLocal2.bI();
      var z$tailLocal1$tmp1 = op.ca(z$tailLocal1, \u03b4this$tailLocal2.m());
      \u03b4this$tailLocal2 = \u03b4this$tailLocal2$tmp1;
      z$tailLocal1 = z$tailLocal1$tmp1;
    }
  }
});
$p.bK = (function() {
  return "LazyList";
});
$p.rr = (function(f) {
  return (((this.bO !== $m_sci_LazyList$Uninitialized$()) && ($p_sci_LazyList__evaluated__sci_LazyList(this) === $m_sci_LazyList$().ah)) ? $m_sci_LazyList$().ah : $p_sci_LazyList__mapImpl__F1__sci_LazyList(this, f));
});
$p.qv = (function(n) {
  return ((n <= 0) ? this : (((this.bO !== $m_sci_LazyList$Uninitialized$()) && ($p_sci_LazyList__evaluated__sci_LazyList(this) === $m_sci_LazyList$().ah)) ? $m_sci_LazyList$().ah : $m_sci_LazyList$().rU(this, n)));
});
$p.cl = (function(sb, start, sep, end) {
  this.qO();
  $p_sci_LazyList__addStringNoForce__jl_StringBuilder__T__T__T__jl_StringBuilder(this, sb.bC, start, sep, end);
  return sb;
});
$p.t = (function() {
  return $p_sci_LazyList__addStringNoForce__jl_StringBuilder__T__T__T__jl_StringBuilder(this, $ct_jl_StringBuilder__T__(new $c_jl_StringBuilder(), "LazyList"), "(", ", ", ")").w;
});
$p.aH = (function() {
  return $m_sci_LazyList$();
});
$p.o = (function() {
  return this.bI();
});
$p.bp = (function(elem) {
  $m_sci_LazyList$();
  return $ct_sci_LazyList__O__sci_LazyList__(new $c_sci_LazyList(), elem, this);
});
$p.aa = (function(f) {
  return this.rr(f);
});
$p.ky = (function(n) {
  return this.qv(n);
});
$p.cF = (function(x) {
  return $f_sc_LinearSeqOps__isDefinedAt__I__Z(this, (x | 0));
});
$p.d = (function(v1) {
  return $f_sc_LinearSeqOps__apply__I__O(this, (v1 | 0));
});
function $isArrayOf_sci_LazyList(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.ch)));
}
var $d_sci_LazyList = new $TypeData().i($c_sci_LazyList, "scala.collection.immutable.LazyList", ({
  ch: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  aX: 1,
  aB: 1,
  b0: 1,
  aZ: 1,
  a: 1
}));
/** @constructor */
function $c_sci_WrappedString(self) {
  this.bY = null;
  this.bY = self;
}
$p = $c_sci_WrappedString.prototype = new $h_sci_AbstractSeq();
$p.constructor = $c_sci_WrappedString;
/** @constructor */
function $h_sci_WrappedString() {
}
$h_sci_WrappedString.prototype = $p;
$p.c = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), new $c_sc_StringView(this.bY));
});
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), new $c_sc_StringView(this.bY));
});
$p.d6 = (function() {
  return new $c_sc_IndexedSeqView$Reverse(this);
});
$p.bp = (function(elem) {
  return $f_sc_IndexedSeqOps__prepended__O__O(this, elem);
});
$p.aa = (function(f) {
  return $f_sc_IndexedSeqOps__map__F1__O(this, f);
});
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var this$1 = this.bY;
  var x = this$1.length;
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.q = (function() {
  return this.bY.length;
});
$p.bl = (function() {
  return "IndexedSeq";
});
$p.gn = (function(that) {
  return $f_sci_IndexedSeq__canEqual__O__Z(this, that);
});
$p.cI = (function() {
  return $m_sci_WrappedString$().T();
});
$p.l = (function() {
  return this.bY.length;
});
$p.t = (function() {
  return this.bY;
});
$p.bc = (function(xs, start, len) {
  if ((xs instanceof $ac_C)) {
    var this$1 = this.bY;
    var srcLen = this$1.length;
    var destLen = xs.a.length;
    var limit = ((len < srcLen) ? len : srcLen);
    var capacity = ((start < 0) ? destLen : ((destLen - start) | 0));
    var total = ((capacity < limit) ? capacity : limit);
    var copied = ((total < 0) ? 0 : total);
    $f_T__getChars__I__I__AC__I__V(this.bY, 0, copied, xs, start);
    return copied;
  } else {
    return $f_sc_IterableOnceOps__copyToArray__O__I__I__I(this, xs, start, len);
  }
});
$p.f3 = (function(o) {
  return ((o instanceof $c_sci_WrappedString) ? (this.bY === o.bY) : $f_sci_IndexedSeq__sameElements__sc_IterableOnce__Z(this, o));
});
$p.bK = (function() {
  return "WrappedString";
});
$p.hC = (function() {
  return 2147483647;
});
$p.p = (function(other) {
  return ((other instanceof $c_sci_WrappedString) ? (this.bY === other.bY) : $f_sc_Seq__equals__O__Z(this, other));
});
$p.r = (function(i) {
  return $bC(this.bY.charCodeAt(i));
});
$p.d = (function(v1) {
  var i = (v1 | 0);
  return $bC(this.bY.charCodeAt(i));
});
$p.gr = (function(coll) {
  return $m_sci_WrappedString$().om(coll);
});
$p.en = (function(coll) {
  return $m_sci_WrappedString$().om(coll);
});
$p.aH = (function() {
  return $m_sci_IndexedSeq$();
});
function $isArrayOf_sci_WrappedString(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cs)));
}
var $d_sci_WrappedString = new $TypeData().i($c_sci_WrappedString, "scala.collection.immutable.WrappedString", ({
  cs: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  a: 1
}));
/** @constructor */
function $c_sjsr_WrappedVarArgs(array) {
  this.kh = null;
  this.kh = array;
}
$p = $c_sjsr_WrappedVarArgs.prototype = new $h_O();
$p.constructor = $c_sjsr_WrappedVarArgs;
/** @constructor */
function $h_sjsr_WrappedVarArgs() {
}
$h_sjsr_WrappedVarArgs.prototype = $p;
$p.cb = (function(f) {
  return $f_sci_StrictOptimizedSeqOps__distinctBy__F1__O(this, f);
});
$p.bH = (function(ord) {
  return $f_sc_SeqOps__sorted__s_math_Ordering__O(this, ord);
});
$p.bp = (function(elem) {
  return $f_sc_StrictOptimizedSeqOps__prepended__O__O(this, elem);
});
$p.aa = (function(f) {
  return $f_sc_StrictOptimizedIterableOps__map__F1__O(this, f);
});
$p.gn = (function(that) {
  return $f_sci_IndexedSeq__canEqual__O__Z(this, that);
});
$p.f3 = (function(o) {
  return $f_sci_IndexedSeq__sameElements__sc_IterableOnce__Z(this, o);
});
$p.hC = (function() {
  return $m_sci_IndexedSeqDefaults$().mT;
});
$p.c = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), new $c_sc_IndexedSeqView$Id(this));
});
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), new $c_sc_IndexedSeqView$Id(this));
});
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var x = this.l();
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.q = (function() {
  return this.l();
});
$p.p = (function(o) {
  return $f_sc_Seq__equals__O__Z(this, o);
});
$p.u = (function() {
  return $m_s_util_hashing_MurmurHash3$().l5(this);
});
$p.t = (function() {
  return $f_sc_Iterable__toString__T(this);
});
$p.dU = (function(p, from) {
  return $f_sc_Iterator__indexWhere__F1__I__I($ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), new $c_sc_IndexedSeqView$Id(this)), p, from);
});
$p.g = (function() {
  return $f_sc_SeqOps__isEmpty__Z(this);
});
$p.bQ = (function(x, default$1) {
  return $f_s_PartialFunction__applyOrElse__O__F1__O(this, x, default$1);
});
$p.f1 = (function(v1) {
  return (this.r(v1) | 0);
});
$p.cI = (function() {
  return $m_sjsr_WrappedVarArgs$().T();
});
$p.dO = (function(f) {
  $f_sc_IterableOnceOps__foreach__F1__V(this, f);
});
$p.fC = (function(p) {
  return $f_sc_IterableOnceOps__exists__F1__Z(this, p);
});
$p.d4 = (function(z, op) {
  return $f_sc_IterableOnceOps__foldLeft__O__F2__O(this, z, op);
});
$p.bc = (function(dest, start, n) {
  return $f_sc_IterableOnceOps__copyToArray__O__I__I__I(this, dest, start, n);
});
$p.cl = (function(b, start, sep, end) {
  return $f_sc_IterableOnceOps__addString__scm_StringBuilder__T__T__T__scm_StringBuilder(this, b, start, sep, end);
});
$p.c2 = (function() {
  $m_sci_List$();
  return $m_sci_Nil$().cc(this);
});
$p.ep = (function() {
  return $m_sjsr_WrappedVarArgs$();
});
$p.l = (function() {
  return (this.kh.length | 0);
});
$p.r = (function(idx) {
  return this.kh[idx];
});
$p.bK = (function() {
  return "WrappedVarArgs";
});
$p.en = (function(coll) {
  return $m_sjsr_WrappedVarArgs$().kK(coll);
});
$p.cF = (function(x) {
  return $f_sc_SeqOps__isDefinedAt__I__Z(this, (x | 0));
});
$p.d = (function(v1) {
  return this.r((v1 | 0));
});
$p.aH = (function() {
  return $m_sjsr_WrappedVarArgs$();
});
var $d_sjsr_WrappedVarArgs = new $TypeData().i($c_sjsr_WrappedVarArgs, "scala.scalajs.runtime.WrappedVarArgs", ({
  j4: 1,
  A: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  q: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  D: 1,
  l: 1,
  u: 1,
  a: 1
}));
/** @constructor */
function $c_sci_HashMap(rootNode) {
  this.bN = null;
  this.bN = rootNode;
}
$p = $c_sci_HashMap.prototype = new $h_sci_AbstractMap();
$p.constructor = $c_sci_HashMap;
/** @constructor */
function $h_sci_HashMap() {
}
$h_sci_HashMap.prototype = $p;
$p.aa = (function(f) {
  return $f_sc_StrictOptimizedIterableOps__map__F1__O(this, f);
});
$p.j1 = (function() {
  return $m_sci_HashMap$();
});
$p.q = (function() {
  return this.bN.bs;
});
$p.M = (function() {
  return this.bN.bs;
});
$p.g = (function() {
  return (this.bN.bs === 0);
});
$p.c = (function() {
  return (this.g() ? $m_sc_Iterator$().G : new $c_sci_MapKeyValueTupleIterator(this.bN));
});
$p.bb = (function(key) {
  var keyUnimprovedHash = $m_sr_Statics$().y(key);
  var keyHash = $m_sc_Hashing$().bD(keyUnimprovedHash);
  return this.bN.ku(key, keyUnimprovedHash, keyHash, 0);
});
$p.d = (function(key) {
  var keyUnimprovedHash = $m_sr_Statics$().y(key);
  var keyHash = $m_sc_Hashing$().bD(keyUnimprovedHash);
  return this.bN.ko(key, keyUnimprovedHash, keyHash, 0);
});
$p.cn = (function(key) {
  var keyUnimprovedHash = $m_sr_Statics$().y(key);
  var keyHash = $m_sc_Hashing$().bD(keyUnimprovedHash);
  return this.bN.iV(key, keyUnimprovedHash, keyHash, 0);
});
$p.eo = (function(key, default$1) {
  var keyUnimprovedHash = $m_sr_Statics$().y(key);
  var keyHash = $m_sc_Hashing$().bD(keyUnimprovedHash);
  return this.bN.kN(key, keyUnimprovedHash, keyHash, 0, default$1);
});
$p.fM = (function(key, value) {
  var keyUnimprovedHash = $m_sr_Statics$().y(key);
  var newRootNode = this.bN.p5(key, value, keyUnimprovedHash, $m_sc_Hashing$().bD(keyUnimprovedHash), 0, true);
  return ((newRootNode === this.bN) ? this : new $c_sci_HashMap(newRootNode));
});
$p.dm = (function(f) {
  this.bN.dm(f);
});
$p.p = (function(that) {
  if ((that instanceof $c_sci_HashMap)) {
    if ((this === that)) {
      return true;
    } else {
      var x = this.bN;
      var x$2 = that.bN;
      return ((x === null) ? (x$2 === null) : x.p(x$2));
    }
  } else {
    return $f_sc_Map__equals__O__Z(this, that);
  }
});
$p.u = (function() {
  if (this.g()) {
    return $m_s_util_hashing_MurmurHash3$().iH;
  } else {
    var hashIterator = new $c_sci_MapKeyValueTupleHashIterator(this.bN);
    return $m_s_util_hashing_MurmurHash3$().gA(hashIterator, $m_s_util_hashing_MurmurHash3$().el);
  }
});
$p.bK = (function() {
  return "HashMap";
});
$p.fO = (function(key, value) {
  return this.fM(key, value);
});
function $isArrayOf_sci_HashMap(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cf)));
}
var $d_sci_HashMap = new $TypeData().i($c_sci_HashMap, "scala.collection.immutable.HashMap", ({
  cf: 1,
  au: 1,
  ak: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  a9: 1,
  an: 1,
  d: 1,
  a8: 1,
  q: 1,
  aw: 1,
  af: 1,
  l: 1,
  bY: 1,
  gs: 1,
  a: 1,
  X: 1
}));
function $isArrayOf_sci_TreeSeqMap(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.gu)));
}
function $isArrayOf_sci_VectorMap(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.gE)));
}
/** @constructor */
function $c_scm_AbstractBuffer() {
}
$p = $c_scm_AbstractBuffer.prototype = new $h_scm_AbstractSeq();
$p.constructor = $c_scm_AbstractBuffer;
/** @constructor */
function $h_scm_AbstractBuffer() {
}
$h_scm_AbstractBuffer.prototype = $p;
$p.b7 = (function(elems) {
  return $f_scm_Growable__addAll__sc_IterableOnce__scm_Growable(this, elems);
});
/** @constructor */
function $c_scm_AbstractMap() {
}
$p = $c_scm_AbstractMap.prototype = new $h_sc_AbstractMap();
$p.constructor = $c_scm_AbstractMap;
/** @constructor */
function $h_scm_AbstractMap() {
}
$h_scm_AbstractMap.prototype = $p;
$p.aH = (function() {
  return $m_scm_Iterable$();
});
$p.aU = (function() {
  return this;
});
function $ct_scm_HashSet__I__D__($thiz, initialCapacity, loadFactor) {
  $thiz.k3 = loadFactor;
  $thiz.bA = new ($d_scm_HashSet$Node.r().C)($p_scm_HashSet__tableSizeFor__I__I($thiz, initialCapacity));
  $thiz.k4 = $p_scm_HashSet__newThreshold__I__I($thiz, $thiz.bA.a.length);
  $thiz.eU = 0;
  return $thiz;
}
function $ct_scm_HashSet__($thiz) {
  $ct_scm_HashSet__I__D__($thiz, 16, 0.75);
  return $thiz;
}
function $p_scm_HashSet__improveHash__I__I($thiz, originalHash) {
  return (originalHash ^ ((originalHash >>> 16) | 0));
}
function $p_scm_HashSet__addElem__O__I__Z($thiz, elem, hash) {
  var idx = (hash & (($thiz.bA.a.length - 1) | 0));
  matchResult4: {
    var x10 = $thiz.bA.a[idx];
    if ((x10 === null)) {
      $thiz.bA.a[idx] = new $c_scm_HashSet$Node(elem, hash, null);
      break matchResult4;
    }
    var prev = null;
    var n = x10;
    while (((n !== null) && (n.eh <= hash))) {
      if (((n.eh === hash) && $m_sr_BoxesRunTime$().n(elem, n.gk))) {
        return false;
      }
      prev = n;
      n = n.bB;
    }
    if ((prev === null)) {
      $thiz.bA.a[idx] = new $c_scm_HashSet$Node(elem, hash, x10);
    } else {
      var this$3 = prev;
      var n$1 = new $c_scm_HashSet$Node(elem, hash, prev.bB);
      this$3.bB = n$1;
    }
  }
  $thiz.eU = ((1 + $thiz.eU) | 0);
  return true;
}
function $p_scm_HashSet__growTable__I__V($thiz, newlen) {
  var oldlen = $thiz.bA.a.length;
  $thiz.k4 = $p_scm_HashSet__newThreshold__I__I($thiz, newlen);
  if (($thiz.eU === 0)) {
    $thiz.bA = new ($d_scm_HashSet$Node.r().C)(newlen);
  } else {
    $thiz.bA = $m_ju_Arrays$().a9($thiz.bA, newlen);
    var preLow = new $c_scm_HashSet$Node(null, 0, null);
    var preHigh = new $c_scm_HashSet$Node(null, 0, null);
    while ((oldlen < newlen)) {
      var i = 0;
      while ((i < oldlen)) {
        var old = $thiz.bA.a[i];
        if ((old !== null)) {
          preLow.bB = null;
          preHigh.bB = null;
          var lastLow = preLow;
          var lastHigh = preHigh;
          var n = old;
          while ((n !== null)) {
            var next = n.bB;
            if (((n.eh & oldlen) === 0)) {
              var this$2 = lastLow;
              var n$1 = n;
              this$2.bB = n$1;
              lastLow = n;
            } else {
              var this$3 = lastHigh;
              var n$2 = n;
              this$3.bB = n$2;
              lastHigh = n;
            }
            n = next;
          }
          var this$4 = lastLow;
          this$4.bB = null;
          if ((old !== preLow.bB)) {
            $thiz.bA.a[i] = preLow.bB;
          }
          if ((preHigh.bB !== null)) {
            $thiz.bA.a[((i + oldlen) | 0)] = preHigh.bB;
            var this$5 = lastHigh;
            this$5.bB = null;
          }
        }
        i = ((1 + i) | 0);
      }
      oldlen = (oldlen << 1);
    }
  }
}
function $p_scm_HashSet__tableSizeFor__I__I($thiz, capacity) {
  var x = ((capacity - 1) | 0);
  var i = ((x > 4) ? x : 4);
  var x$1 = ((((-2147483648) >> Math.clz32(i)) & i) << 1);
  return ((x$1 < 1073741824) ? x$1 : 1073741824);
}
function $p_scm_HashSet__newThreshold__I__I($thiz, size) {
  return $doubleToInt((size * $thiz.k3));
}
/** @constructor */
function $c_scm_HashSet() {
  this.k3 = 0.0;
  this.bA = null;
  this.k4 = 0;
  this.eU = 0;
}
$p = $c_scm_HashSet.prototype = new $h_scm_AbstractSet();
$p.constructor = $c_scm_HashSet;
/** @constructor */
function $h_scm_HashSet() {
}
$h_scm_HashSet.prototype = $p;
$p.M = (function() {
  return this.eU;
});
$p.bb = (function(elem) {
  var hash = $p_scm_HashSet__improveHash__I__I(this, $m_sr_Statics$().y(elem));
  matchResult1: {
    var $x_1;
    var x1 = this.bA.a[(hash & ((this.bA.a.length - 1) | 0))];
    if ((x1 === null)) {
      var $x_1 = null;
      break matchResult1;
    }
    var $x_1 = x1.qL(elem, hash);
  }
  return ($x_1 !== null);
});
$p.aZ = (function(size) {
  var target = $p_scm_HashSet__tableSizeFor__I__I(this, $doubleToInt((((1 + size) | 0) / this.k3)));
  if ((target > this.bA.a.length)) {
    $p_scm_HashSet__growTable__I__V(this, target);
  }
});
$p.iN = (function(elem) {
  if ((((1 + this.eU) | 0) >= this.k4)) {
    $p_scm_HashSet__growTable__I__V(this, (this.bA.a.length << 1));
  }
  return $p_scm_HashSet__addElem__O__I__Z(this, elem, $p_scm_HashSet__improveHash__I__I(this, $m_sr_Statics$().y(elem)));
});
$p.nK = (function(xs) {
  $f_scm_Builder__sizeHint__sc_IterableOnce__I__V(this, xs, 0);
  if ((xs instanceof $c_sci_HashSet)) {
    var f = new $c_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8(((v1$2, v2$2) => {
      $p_scm_HashSet__addElem__O__I__Z(this, v1$2, $p_scm_HashSet__improveHash__I__I(this, (v2$2 | 0)));
    }));
    xs.c4.kF(f);
    return this;
  } else if ((xs instanceof $c_scm_HashSet)) {
    var iter = new $c_scm_HashSet$$anon$2(xs);
    while (iter.k()) {
      var next = iter.f();
      $p_scm_HashSet__addElem__O__I__Z(this, next.gk, next.eh);
    }
    return this;
  } else if (false) {
    var iter$2 = xs.oa();
    while (iter$2.k()) {
      var next$2 = iter$2.f();
      $p_scm_HashSet__addElem__O__I__Z(this, next$2.ox(), next$2.or());
    }
    return this;
  } else {
    return $f_scm_Growable__addAll__sc_IterableOnce__scm_Growable(this, xs);
  }
});
$p.c = (function() {
  return new $c_scm_HashSet$$anon$1(this);
});
$p.aH = (function() {
  return $m_scm_HashSet$();
});
$p.q = (function() {
  return this.eU;
});
$p.g = (function() {
  return (this.eU === 0);
});
$p.bK = (function() {
  return "HashSet";
});
$p.u = (function() {
  var setIterator = new $c_scm_HashSet$$anon$1(this);
  var hashIterator = ((!setIterator.k()) ? setIterator : new $c_scm_HashSet$$anon$3(this));
  return $m_s_util_hashing_MurmurHash3$().gA(hashIterator, $m_s_util_hashing_MurmurHash3$().iI);
});
$p.b7 = (function(elems) {
  return this.nK(elems);
});
$p.aq = (function(elem) {
  this.iN(elem);
  return this;
});
function $isArrayOf_scm_HashSet(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cF)));
}
var $d_scm_HashSet = new $TypeData().i($c_scm_HashSet, "scala.collection.mutable.HashSet", ({
  cF: 1,
  gI: 1,
  al: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  ao: 1,
  d: 1,
  aa: 1,
  N: 1,
  E: 1,
  M: 1,
  H: 1,
  I: 1,
  K: 1,
  aG: 1,
  hj: 1,
  hi: 1,
  l: 1,
  a: 1
}));
function $isArrayOf_sci_ListMap(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.fW)));
}
function $isArrayOf_scm_LinkedHashSet(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.hc)));
}
/** @constructor */
function $c_sci_ArraySeq() {
}
$p = $c_sci_ArraySeq.prototype = new $h_sci_AbstractSeq();
$p.constructor = $c_sci_ArraySeq;
/** @constructor */
function $h_sci_ArraySeq() {
}
$h_sci_ArraySeq.prototype = $p;
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), new $c_sc_IndexedSeqView$Id(this));
});
$p.d6 = (function() {
  return new $c_sc_IndexedSeqView$Reverse(this);
});
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var x = this.l();
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.q = (function() {
  return this.l();
});
$p.bl = (function() {
  return "IndexedSeq";
});
$p.gn = (function(that) {
  return $f_sci_IndexedSeq__canEqual__O__Z(this, that);
});
$p.f3 = (function(o) {
  return $f_sci_IndexedSeq__sameElements__sc_IterableOnce__Z(this, o);
});
$p.cb = (function(f) {
  return $f_sci_StrictOptimizedSeqOps__distinctBy__F1__O(this, f);
});
$p.gr = (function(coll) {
  return $m_sci_ArraySeq$().kI(coll, this.aS());
});
$p.cI = (function() {
  return $m_sci_ArraySeq$().j2(this.aS());
});
$p.ep = (function() {
  return $m_sci_ArraySeq$().jP;
});
$p.rq = (function(f) {
  var a = new $ac_O(this.l());
  var i = 0;
  while ((i < a.a.length)) {
    a.a[i] = f.d(this.r(i));
    i = ((1 + i) | 0);
  }
  return $m_sci_ArraySeq$().gB(a);
});
$p.aT = (function(elem) {
  var $x_1 = $m_sci_ArraySeq$();
  var xs = this.cr();
  var dest = new $ac_O(((1 + $m_jl_reflect_Array$().b8(xs)) | 0));
  dest.a[0] = elem;
  $m_s_Array$().bT(xs, 0, dest, 1, $m_jl_reflect_Array$().b8(xs));
  return $x_1.gB(dest);
});
$p.d4 = (function(z, f) {
  var array = this.cr();
  var b = z;
  var i = 0;
  while ((i < $m_jl_reflect_Array$().b8(array))) {
    var a = $m_sr_ScalaRunTime$().aM(array, i);
    b = f.ca(b, a);
    i = ((1 + i) | 0);
  }
  return b;
});
$p.bK = (function() {
  return "ArraySeq";
});
$p.bc = (function(xs, start, len) {
  var srcLen = this.l();
  var destLen = $m_jl_reflect_Array$().b8(xs);
  var limit = ((len < srcLen) ? len : srcLen);
  var capacity = ((start < 0) ? destLen : ((destLen - start) | 0));
  var total = ((capacity < limit) ? capacity : limit);
  var copied = ((total < 0) ? 0 : total);
  if ((copied > 0)) {
    $m_s_Array$().bT(this.cr(), 0, xs, start, copied);
  }
  return copied;
});
$p.hC = (function() {
  return 2147483647;
});
$p.bq = (function(ord) {
  if (($m_jl_reflect_Array$().b8(this.cr()) <= 1)) {
    return this;
  } else {
    var original = this.cr();
    var newLength = this.l();
    $m_s_reflect_ManifestFactory$ObjectManifest$();
    if ($d_O.R($objectGetClass(original).J.Q().J)) {
      var a = $m_ju_Arrays$().kv(original, newLength, $d_O.r().l());
    } else {
      var dest = new $ac_O(newLength);
      $m_s_Array$().bT(original, 0, dest, 0, $m_jl_reflect_Array$().b8(original));
      var a = dest;
    }
    $m_ju_Arrays$().gz(a, ord);
    return new $c_sci_ArraySeq$ofRef(a);
  }
});
$p.aH = (function() {
  return $m_sci_ArraySeq$().jP;
});
$p.aa = (function(f) {
  return this.rq(f);
});
$p.bp = (function(elem) {
  return this.aT(elem);
});
$p.bH = (function(ord) {
  return this.bq(ord);
});
$p.en = (function(coll) {
  return $m_sci_ArraySeq$().kI(coll, this.aS());
});
function $isArrayOf_sci_ArraySeq(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.a3)));
}
function $ct_sci_Vector__AO__($thiz, prefix1) {
  $thiz.e = prefix1;
  return $thiz;
}
/** @constructor */
function $c_sci_Vector() {
  this.e = null;
}
$p = $c_sci_Vector.prototype = new $h_sci_AbstractSeq();
$p.constructor = $c_sci_Vector;
/** @constructor */
function $h_sci_Vector() {
}
$h_sci_Vector.prototype = $p;
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), new $c_sc_IndexedSeqView$Id(this));
});
$p.d6 = (function() {
  return new $c_sc_IndexedSeqView$Reverse(this);
});
$p.aI = (function(len) {
  var x = this.l();
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.q = (function() {
  return this.l();
});
$p.bl = (function() {
  return "IndexedSeq";
});
$p.gn = (function(that) {
  return $f_sci_IndexedSeq__canEqual__O__Z(this, that);
});
$p.f3 = (function(o) {
  return $f_sci_IndexedSeq__sameElements__sc_IterableOnce__Z(this, o);
});
$p.cb = (function(f) {
  return $f_sci_StrictOptimizedSeqOps__distinctBy__F1__O(this, f);
});
$p.bH = (function(ord) {
  return $f_sc_SeqOps__sorted__s_math_Ordering__O(this, ord);
});
$p.ep = (function() {
  return $m_sci_Vector$();
});
$p.l = (function() {
  return ((this instanceof $c_sci_BigVector) ? this.j : this.e.a.length);
});
$p.c = (function() {
  return ((this === $m_sci_Vector0$()) ? $m_sci_Vector$().n3 : new $c_sci_NewVectorIterator(this, this.l(), this.dY()));
});
$p.bK = (function() {
  return "Vector";
});
$p.bc = (function(xs, start, len) {
  return this.c().bc(xs, start, len);
});
$p.hC = (function() {
  return $m_sci_Vector$().n2;
});
$p.bi = (function(index) {
  return $m_scg_CommonErrors$().kQ(index, ((this.l() - 1) | 0));
});
$p.m = (function() {
  if ((this.e.a.length === 0)) {
    throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), "empty.head");
  } else {
    return this.e.a[0];
  }
});
$p.dO = (function(f) {
  var c = this.dY();
  var i = 0;
  while ((i < c)) {
    var $x_1 = $m_sci_VectorStatics$();
    var idx = i;
    var c$1 = (((c + ((c >>> 31) | 0)) | 0) >> 1);
    var a = ((idx - c$1) | 0);
    var sign = (a >> 31);
    $x_1.kE(((((((1 + c$1) | 0) - (((a ^ sign) - sign) | 0)) | 0) - 1) | 0), this.dX(i), f);
    i = ((1 + i) | 0);
  }
});
$p.aH = (function() {
  return $m_sci_Vector$();
});
function $isArrayOf_sci_Vector(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.ag)));
}
/** @constructor */
function $c_scm_ArraySeq() {
}
$p = $c_scm_ArraySeq.prototype = new $h_scm_AbstractSeq();
$p.constructor = $c_scm_ArraySeq;
/** @constructor */
function $h_scm_ArraySeq() {
}
$h_scm_ArraySeq.prototype = $p;
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), new $c_sc_IndexedSeqView$Id(this));
});
$p.d6 = (function() {
  return new $c_sc_IndexedSeqView$Reverse(this);
});
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var x = this.l();
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.q = (function() {
  return this.l();
});
$p.bl = (function() {
  return "IndexedSeq";
});
$p.aa = (function(f) {
  return $f_sc_StrictOptimizedIterableOps__map__F1__O(this, f);
});
$p.cb = (function(f) {
  return $f_sc_StrictOptimizedSeqOps__distinctBy__F1__O(this, f);
});
$p.ep = (function() {
  return $m_scm_ArraySeq$().k0;
});
$p.on = (function(coll) {
  var evidence$1 = this.aS();
  var capacity = 0;
  var size = 0;
  var jsElems = null;
  var elementClass = evidence$1.bk();
  capacity = 0;
  size = 0;
  var isCharArrayBuilder = (elementClass === $d_C.l());
  jsElems = [];
  coll.q();
  var it = coll.c();
  while (it.k()) {
    var elem = it.f();
    var unboxedElem = (isCharArrayBuilder ? $uC(elem) : ((elem === null) ? $m_scm_ArrayBuilder$().j7(elementClass) : elem));
    jsElems.push(unboxedElem);
  }
  return $m_scm_ArraySeq$().j0($m_scm_ArrayBuilder$().j6(((elementClass === $d_V.l()) ? $d_jl_Void.l() : (((elementClass === $d_sr_Null$.l()) || (elementClass === $d_sr_Nothing$.l())) ? $d_O.l() : elementClass)), jsElems));
});
$p.cI = (function() {
  return $m_scm_ArraySeq$().j2(this.aS());
});
$p.bK = (function() {
  return "ArraySeq";
});
$p.bc = (function(xs, start, len) {
  var srcLen = this.l();
  var destLen = $m_jl_reflect_Array$().b8(xs);
  var limit = ((len < srcLen) ? len : srcLen);
  var capacity = ((start < 0) ? destLen : ((destLen - start) | 0));
  var total = ((capacity < limit) ? capacity : limit);
  var copied = ((total < 0) ? 0 : total);
  if ((copied > 0)) {
    $m_s_Array$().bT(this.cD(), 0, xs, start, copied);
  }
  return copied;
});
$p.p = (function(other) {
  if ((other instanceof $c_scm_ArraySeq)) {
    if (($m_jl_reflect_Array$().b8(this.cD()) !== $m_jl_reflect_Array$().b8(other.cD()))) {
      return false;
    }
  }
  return $f_sc_Seq__equals__O__Z(this, other);
});
$p.sc = (function(ord) {
  return $m_scm_ArraySeq$().j0($m_sc_ArrayOps$().sd(this.cD(), ord));
});
$p.aH = (function() {
  return $m_scm_ArraySeq$().k0;
});
$p.gr = (function(coll) {
  return this.on(coll);
});
$p.en = (function(coll) {
  return this.on(coll);
});
$p.bH = (function(ord) {
  return this.sc(ord);
});
function $isArrayOf_scm_ArraySeq(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.a4)));
}
/** @constructor */
function $c_sci_ArraySeq$ofBoolean(unsafeArray) {
  this.da = null;
  this.da = unsafeArray;
}
$p = $c_sci_ArraySeq$ofBoolean.prototype = new $h_sci_ArraySeq();
$p.constructor = $c_sci_ArraySeq$ofBoolean;
/** @constructor */
function $h_sci_ArraySeq$ofBoolean() {
}
$h_sci_ArraySeq$ofBoolean.prototype = $p;
$p.l = (function() {
  return this.da.a.length;
});
$p.hA = (function(i) {
  return this.da.a[i];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.da, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_sci_ArraySeq$ofBoolean) ? $m_ju_Arrays$().of(this.da, that.da) : $f_sc_Seq__equals__O__Z(this, that));
});
$p.bq = (function(ord) {
  if ((this.da.a.length <= 1)) {
    return this;
  } else if ((ord === ($m_s_math_Ordering$(), $m_s_math_Ordering$Boolean$()))) {
    var this$1 = this.da;
    var a = this$1.h();
    $m_s_util_Sorting$().l8(a, 0, a.a.length, $m_s_math_Ordering$Boolean$());
    return new $c_sci_ArraySeq$ofBoolean(a);
  } else {
    return $c_sci_ArraySeq.prototype.bq.call(this, ord);
  }
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.da);
});
$p.aT = (function(elem) {
  if (((typeof elem) === "boolean")) {
    var b = (!(!elem));
    var xs = this.da;
    $m_s_reflect_ManifestFactory$BooleanManifest$();
    var dest = new $ac_Z(((1 + xs.a.length) | 0));
    dest.a[0] = b;
    $m_s_Array$().bT(xs, 0, dest, 1, xs.a.length);
    return new $c_sci_ArraySeq$ofBoolean(dest);
  } else {
    return $c_sci_ArraySeq.prototype.aT.call(this, elem);
  }
});
$p.cr = (function() {
  return this.da;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$BooleanManifest$();
});
$p.r = (function(i) {
  return this.hA(i);
});
$p.d = (function(v1) {
  return this.hA((v1 | 0));
});
$p.bH = (function(ord) {
  return this.bq(ord);
});
$p.bp = (function(elem) {
  return this.aT(elem);
});
function $isArrayOf_sci_ArraySeq$ofBoolean(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.c1)));
}
var $d_sci_ArraySeq$ofBoolean = new $TypeData().i($c_sci_ArraySeq$ofBoolean, "scala.collection.immutable.ArraySeq$ofBoolean", ({
  c1: 1,
  a3: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a6: 1,
  a: 1
}));
/** @constructor */
function $c_sci_ArraySeq$ofByte(unsafeArray) {
  this.db = null;
  this.db = unsafeArray;
}
$p = $c_sci_ArraySeq$ofByte.prototype = new $h_sci_ArraySeq();
$p.constructor = $c_sci_ArraySeq$ofByte;
/** @constructor */
function $h_sci_ArraySeq$ofByte() {
}
$h_sci_ArraySeq$ofByte.prototype = $p;
$p.l = (function() {
  return this.db.a.length;
});
$p.hs = (function(i) {
  return this.db.a[i];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.db, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_sci_ArraySeq$ofByte) ? $m_ju_Arrays$().ob(this.db, that.db) : $f_sc_Seq__equals__O__Z(this, that));
});
$p.bq = (function(ord) {
  if ((this.db.a.length <= 1)) {
    return this;
  } else if ((ord === ($m_s_math_Ordering$(), $m_s_math_Ordering$Byte$()))) {
    var this$1 = this.db;
    var a = this$1.h();
    $m_ju_Arrays$().oQ(a);
    return new $c_sci_ArraySeq$ofByte(a);
  } else {
    return $c_sci_ArraySeq.prototype.bq.call(this, ord);
  }
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.db);
});
$p.aT = (function(elem) {
  if ($isByte(elem)) {
    var b = (elem | 0);
    var xs = this.db;
    $m_s_reflect_ManifestFactory$ByteManifest$();
    var dest = new $ac_B(((1 + xs.a.length) | 0));
    dest.a[0] = b;
    $m_s_Array$().bT(xs, 0, dest, 1, xs.a.length);
    return new $c_sci_ArraySeq$ofByte(dest);
  } else {
    return $c_sci_ArraySeq.prototype.aT.call(this, elem);
  }
});
$p.cr = (function() {
  return this.db;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$ByteManifest$();
});
$p.r = (function(i) {
  return this.hs(i);
});
$p.d = (function(v1) {
  return this.hs((v1 | 0));
});
$p.bH = (function(ord) {
  return this.bq(ord);
});
$p.bp = (function(elem) {
  return this.aT(elem);
});
function $isArrayOf_sci_ArraySeq$ofByte(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.c2)));
}
var $d_sci_ArraySeq$ofByte = new $TypeData().i($c_sci_ArraySeq$ofByte, "scala.collection.immutable.ArraySeq$ofByte", ({
  c2: 1,
  a3: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a6: 1,
  a: 1
}));
/** @constructor */
function $c_sci_ArraySeq$ofChar(unsafeArray) {
  this.cP = null;
  this.cP = unsafeArray;
}
$p = $c_sci_ArraySeq$ofChar.prototype = new $h_sci_ArraySeq();
$p.constructor = $c_sci_ArraySeq$ofChar;
/** @constructor */
function $h_sci_ArraySeq$ofChar() {
}
$h_sci_ArraySeq$ofChar.prototype = $p;
$p.l = (function() {
  return this.cP.a.length;
});
$p.ht = (function(i) {
  return this.cP.a[i];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.cP, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_sci_ArraySeq$ofChar) ? $m_ju_Arrays$().oc(this.cP, that.cP) : $f_sc_Seq__equals__O__Z(this, that));
});
$p.bq = (function(ord) {
  if ((this.cP.a.length <= 1)) {
    return this;
  } else if ((ord === ($m_s_math_Ordering$(), $m_s_math_Ordering$Char$()))) {
    var this$1 = this.cP;
    var a = this$1.h();
    $m_ju_Arrays$().oR(a);
    return new $c_sci_ArraySeq$ofChar(a);
  } else {
    return $c_sci_ArraySeq.prototype.bq.call(this, ord);
  }
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.cP);
});
$p.aT = (function(elem) {
  if ((elem instanceof $Char)) {
    var b = $uC(elem);
    var xs = this.cP;
    $m_s_reflect_ManifestFactory$CharManifest$();
    var dest = new $ac_C(((1 + xs.a.length) | 0));
    dest.a[0] = b;
    $m_s_Array$().bT(xs, 0, dest, 1, xs.a.length);
    return new $c_sci_ArraySeq$ofChar(dest);
  } else {
    return $c_sci_ArraySeq.prototype.aT.call(this, elem);
  }
});
$p.cl = (function(sb, start, sep, end) {
  return new $c_scm_ArraySeq$ofChar(this.cP).cl(sb, start, sep, end);
});
$p.cr = (function() {
  return this.cP;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$CharManifest$();
});
$p.r = (function(i) {
  return $bC(this.ht(i));
});
$p.d = (function(v1) {
  return $bC(this.ht((v1 | 0)));
});
$p.bH = (function(ord) {
  return this.bq(ord);
});
$p.bp = (function(elem) {
  return this.aT(elem);
});
function $isArrayOf_sci_ArraySeq$ofChar(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.c3)));
}
var $d_sci_ArraySeq$ofChar = new $TypeData().i($c_sci_ArraySeq$ofChar, "scala.collection.immutable.ArraySeq$ofChar", ({
  c3: 1,
  a3: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a6: 1,
  a: 1
}));
/** @constructor */
function $c_sci_ArraySeq$ofDouble(unsafeArray) {
  this.e6 = null;
  this.e6 = unsafeArray;
}
$p = $c_sci_ArraySeq$ofDouble.prototype = new $h_sci_ArraySeq();
$p.constructor = $c_sci_ArraySeq$ofDouble;
/** @constructor */
function $h_sci_ArraySeq$ofDouble() {
}
$h_sci_ArraySeq$ofDouble.prototype = $p;
$p.l = (function() {
  return this.e6.a.length;
});
$p.hu = (function(i) {
  return this.e6.a[i];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.e6, this$1.aY);
});
$p.p = (function(that) {
  if ((that instanceof $c_sci_ArraySeq$ofDouble)) {
    var array = this.e6;
    var thatArray = that.e6;
    if ((array === thatArray)) {
      return true;
    } else if ((array.a.length === thatArray.a.length)) {
      var i = 0;
      while (((i < array.a.length) && (array.a[i] === thatArray.a[i]))) {
        i = ((1 + i) | 0);
      }
      return (i >= array.a.length);
    } else {
      return false;
    }
  } else {
    return $f_sc_Seq__equals__O__Z(this, that);
  }
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.e6);
});
$p.aT = (function(elem) {
  if (((typeof elem) === "number")) {
    var b = (+elem);
    var xs = this.e6;
    $m_s_reflect_ManifestFactory$DoubleManifest$();
    var dest = new $ac_D(((1 + xs.a.length) | 0));
    dest.a[0] = b;
    $m_s_Array$().bT(xs, 0, dest, 1, xs.a.length);
    return new $c_sci_ArraySeq$ofDouble(dest);
  } else {
    return $c_sci_ArraySeq.prototype.aT.call(this, elem);
  }
});
$p.cr = (function() {
  return this.e6;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$DoubleManifest$();
});
$p.r = (function(i) {
  return this.hu(i);
});
$p.d = (function(v1) {
  return this.hu((v1 | 0));
});
$p.bp = (function(elem) {
  return this.aT(elem);
});
function $isArrayOf_sci_ArraySeq$ofDouble(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.c4)));
}
var $d_sci_ArraySeq$ofDouble = new $TypeData().i($c_sci_ArraySeq$ofDouble, "scala.collection.immutable.ArraySeq$ofDouble", ({
  c4: 1,
  a3: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a6: 1,
  a: 1
}));
/** @constructor */
function $c_sci_ArraySeq$ofFloat(unsafeArray) {
  this.e7 = null;
  this.e7 = unsafeArray;
}
$p = $c_sci_ArraySeq$ofFloat.prototype = new $h_sci_ArraySeq();
$p.constructor = $c_sci_ArraySeq$ofFloat;
/** @constructor */
function $h_sci_ArraySeq$ofFloat() {
}
$h_sci_ArraySeq$ofFloat.prototype = $p;
$p.l = (function() {
  return this.e7.a.length;
});
$p.hv = (function(i) {
  return this.e7.a[i];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.e7, this$1.aY);
});
$p.p = (function(that) {
  if ((that instanceof $c_sci_ArraySeq$ofFloat)) {
    var array = this.e7;
    var thatArray = that.e7;
    if ((array === thatArray)) {
      return true;
    } else if ((array.a.length === thatArray.a.length)) {
      var i = 0;
      while (((i < array.a.length) && (array.a[i] === thatArray.a[i]))) {
        i = ((1 + i) | 0);
      }
      return (i >= array.a.length);
    } else {
      return false;
    }
  } else {
    return $f_sc_Seq__equals__O__Z(this, that);
  }
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.e7);
});
$p.aT = (function(elem) {
  if ($isFloat(elem)) {
    var b = Math.fround(elem);
    var xs = this.e7;
    $m_s_reflect_ManifestFactory$FloatManifest$();
    var dest = new $ac_F(((1 + xs.a.length) | 0));
    dest.a[0] = b;
    $m_s_Array$().bT(xs, 0, dest, 1, xs.a.length);
    return new $c_sci_ArraySeq$ofFloat(dest);
  } else {
    return $c_sci_ArraySeq.prototype.aT.call(this, elem);
  }
});
$p.cr = (function() {
  return this.e7;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$FloatManifest$();
});
$p.r = (function(i) {
  return this.hv(i);
});
$p.d = (function(v1) {
  return this.hv((v1 | 0));
});
$p.bp = (function(elem) {
  return this.aT(elem);
});
function $isArrayOf_sci_ArraySeq$ofFloat(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.c5)));
}
var $d_sci_ArraySeq$ofFloat = new $TypeData().i($c_sci_ArraySeq$ofFloat, "scala.collection.immutable.ArraySeq$ofFloat", ({
  c5: 1,
  a3: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a6: 1,
  a: 1
}));
/** @constructor */
function $c_sci_ArraySeq$ofInt(unsafeArray) {
  this.dc = null;
  this.dc = unsafeArray;
}
$p = $c_sci_ArraySeq$ofInt.prototype = new $h_sci_ArraySeq();
$p.constructor = $c_sci_ArraySeq$ofInt;
/** @constructor */
function $h_sci_ArraySeq$ofInt() {
}
$h_sci_ArraySeq$ofInt.prototype = $p;
$p.l = (function() {
  return this.dc.a.length;
});
$p.hw = (function(i) {
  return this.dc.a[i];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.dc, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_sci_ArraySeq$ofInt) ? $m_ju_Arrays$().iS(this.dc, that.dc) : $f_sc_Seq__equals__O__Z(this, that));
});
$p.bq = (function(ord) {
  if ((this.dc.a.length <= 1)) {
    return this;
  } else if ((ord === ($m_s_math_Ordering$(), $m_s_math_Ordering$Int$()))) {
    var this$1 = this.dc;
    var a = this$1.h();
    $m_ju_Arrays$().oS(a);
    return new $c_sci_ArraySeq$ofInt(a);
  } else {
    return $c_sci_ArraySeq.prototype.bq.call(this, ord);
  }
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.dc);
});
$p.aT = (function(elem) {
  if ($isInt(elem)) {
    var b = (elem | 0);
    var xs = this.dc;
    $m_s_reflect_ManifestFactory$IntManifest$();
    var dest = new $ac_I(((1 + xs.a.length) | 0));
    dest.a[0] = b;
    $m_s_Array$().bT(xs, 0, dest, 1, xs.a.length);
    return new $c_sci_ArraySeq$ofInt(dest);
  } else {
    return $c_sci_ArraySeq.prototype.aT.call(this, elem);
  }
});
$p.cr = (function() {
  return this.dc;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$IntManifest$();
});
$p.r = (function(i) {
  return this.hw(i);
});
$p.d = (function(v1) {
  return this.hw((v1 | 0));
});
$p.bH = (function(ord) {
  return this.bq(ord);
});
$p.bp = (function(elem) {
  return this.aT(elem);
});
function $isArrayOf_sci_ArraySeq$ofInt(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.c6)));
}
var $d_sci_ArraySeq$ofInt = new $TypeData().i($c_sci_ArraySeq$ofInt, "scala.collection.immutable.ArraySeq$ofInt", ({
  c6: 1,
  a3: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a6: 1,
  a: 1
}));
/** @constructor */
function $c_sci_ArraySeq$ofLong(unsafeArray) {
  this.dd = null;
  this.dd = unsafeArray;
}
$p = $c_sci_ArraySeq$ofLong.prototype = new $h_sci_ArraySeq();
$p.constructor = $c_sci_ArraySeq$ofLong;
/** @constructor */
function $h_sci_ArraySeq$ofLong() {
}
$h_sci_ArraySeq$ofLong.prototype = $p;
$p.l = (function() {
  return ((this.dd.a.length >>> 1) | 0);
});
$p.hx = (function(i) {
  var $x_1 = this.dd.a;
  var $x_2 = (i << 1);
  return $bL($x_1[$x_2], $x_1[(($x_2 + 1) | 0)]);
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.dd, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_sci_ArraySeq$ofLong) ? $m_ju_Arrays$().od(this.dd, that.dd) : $f_sc_Seq__equals__O__Z(this, that));
});
$p.bq = (function(ord) {
  if ((((this.dd.a.length >>> 1) | 0) <= 1)) {
    return this;
  } else if ((ord === ($m_s_math_Ordering$(), $m_s_math_Ordering$Long$()))) {
    var this$1 = this.dd;
    var a = this$1.h();
    $m_ju_Arrays$().oT(a);
    return new $c_sci_ArraySeq$ofLong(a);
  } else {
    return $c_sci_ArraySeq.prototype.bq.call(this, ord);
  }
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.dd);
});
$p.aT = (function(elem) {
  if ((elem instanceof $Long)) {
    var $x_1 = $uJ(elem);
    var b_$_lo = $x_1.l;
    var b_$_hi = $x_1.h;
    var xs = this.dd;
    $m_s_reflect_ManifestFactory$LongManifest$();
    var dest = new $ac_J(((1 + ((xs.a.length >>> 1) | 0)) | 0));
    var $x_2 = dest.a;
    $x_2[0] = b_$_lo;
    $x_2[1] = b_$_hi;
    $m_s_Array$().bT(xs, 0, dest, 1, ((xs.a.length >>> 1) | 0));
    return new $c_sci_ArraySeq$ofLong(dest);
  } else {
    return $c_sci_ArraySeq.prototype.aT.call(this, elem);
  }
});
$p.cr = (function() {
  return this.dd;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$LongManifest$();
});
$p.r = (function(i) {
  return this.hx(i);
});
$p.d = (function(v1) {
  return this.hx((v1 | 0));
});
$p.bH = (function(ord) {
  return this.bq(ord);
});
$p.bp = (function(elem) {
  return this.aT(elem);
});
function $isArrayOf_sci_ArraySeq$ofLong(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.c7)));
}
var $d_sci_ArraySeq$ofLong = new $TypeData().i($c_sci_ArraySeq$ofLong, "scala.collection.immutable.ArraySeq$ofLong", ({
  c7: 1,
  a3: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a6: 1,
  a: 1
}));
/** @constructor */
function $c_sci_ArraySeq$ofRef(unsafeArray) {
  this.cQ = null;
  this.cQ = unsafeArray;
}
$p = $c_sci_ArraySeq$ofRef.prototype = new $h_sci_ArraySeq();
$p.constructor = $c_sci_ArraySeq$ofRef;
/** @constructor */
function $h_sci_ArraySeq$ofRef() {
}
$h_sci_ArraySeq$ofRef.prototype = $p;
$p.aS = (function() {
  return $m_s_reflect_ClassTag$().kn($objectGetClass(this.cQ).J.Q());
});
$p.l = (function() {
  return this.cQ.a.length;
});
$p.r = (function(i) {
  return this.cQ.a[i];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.cQ, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_sci_ArraySeq$ofRef) ? $m_s_Array$().og(this.cQ, that.cQ) : $f_sc_Seq__equals__O__Z(this, that));
});
$p.oV = (function(ord) {
  if ((this.cQ.a.length <= 1)) {
    return this;
  } else {
    var this$1 = this.cQ;
    var a = this$1.h();
    $m_ju_Arrays$().gz(a, ord);
    return new $c_sci_ArraySeq$ofRef(a);
  }
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.cQ);
});
$p.cr = (function() {
  return this.cQ;
});
$p.d = (function(v1) {
  return this.r((v1 | 0));
});
$p.bq = (function(ord) {
  return this.oV(ord);
});
$p.bH = (function(ord) {
  return this.oV(ord);
});
function $isArrayOf_sci_ArraySeq$ofRef(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.c8)));
}
var $d_sci_ArraySeq$ofRef = new $TypeData().i($c_sci_ArraySeq$ofRef, "scala.collection.immutable.ArraySeq$ofRef", ({
  c8: 1,
  a3: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a6: 1,
  a: 1
}));
/** @constructor */
function $c_sci_ArraySeq$ofShort(unsafeArray) {
  this.de = null;
  this.de = unsafeArray;
}
$p = $c_sci_ArraySeq$ofShort.prototype = new $h_sci_ArraySeq();
$p.constructor = $c_sci_ArraySeq$ofShort;
/** @constructor */
function $h_sci_ArraySeq$ofShort() {
}
$h_sci_ArraySeq$ofShort.prototype = $p;
$p.l = (function() {
  return this.de.a.length;
});
$p.hy = (function(i) {
  return this.de.a[i];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.de, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_sci_ArraySeq$ofShort) ? $m_ju_Arrays$().oe(this.de, that.de) : $f_sc_Seq__equals__O__Z(this, that));
});
$p.bq = (function(ord) {
  if ((this.de.a.length <= 1)) {
    return this;
  } else if ((ord === ($m_s_math_Ordering$(), $m_s_math_Ordering$Short$()))) {
    var this$1 = this.de;
    var a = this$1.h();
    $m_ju_Arrays$().oU(a);
    return new $c_sci_ArraySeq$ofShort(a);
  } else {
    return $c_sci_ArraySeq.prototype.bq.call(this, ord);
  }
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.de);
});
$p.aT = (function(elem) {
  if ($isShort(elem)) {
    var b = (elem | 0);
    var xs = this.de;
    $m_s_reflect_ManifestFactory$ShortManifest$();
    var dest = new $ac_S(((1 + xs.a.length) | 0));
    dest.a[0] = b;
    $m_s_Array$().bT(xs, 0, dest, 1, xs.a.length);
    return new $c_sci_ArraySeq$ofShort(dest);
  } else {
    return $c_sci_ArraySeq.prototype.aT.call(this, elem);
  }
});
$p.cr = (function() {
  return this.de;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$ShortManifest$();
});
$p.r = (function(i) {
  return this.hy(i);
});
$p.d = (function(v1) {
  return this.hy((v1 | 0));
});
$p.bH = (function(ord) {
  return this.bq(ord);
});
$p.bp = (function(elem) {
  return this.aT(elem);
});
function $isArrayOf_sci_ArraySeq$ofShort(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.c9)));
}
var $d_sci_ArraySeq$ofShort = new $TypeData().i($c_sci_ArraySeq$ofShort, "scala.collection.immutable.ArraySeq$ofShort", ({
  c9: 1,
  a3: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a6: 1,
  a: 1
}));
/** @constructor */
function $c_sci_ArraySeq$ofUnit(unsafeArray) {
  this.fi = null;
  this.fi = unsafeArray;
}
$p = $c_sci_ArraySeq$ofUnit.prototype = new $h_sci_ArraySeq();
$p.constructor = $c_sci_ArraySeq$ofUnit;
/** @constructor */
function $h_sci_ArraySeq$ofUnit() {
}
$h_sci_ArraySeq$ofUnit.prototype = $p;
$p.l = (function() {
  return this.fi.a.length;
});
$p.hz = (function(i) {
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.fi, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_sci_ArraySeq$ofUnit) ? (this.fi.a.length === that.fi.a.length) : $f_sc_Seq__equals__O__Z(this, that));
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.fi);
});
$p.cr = (function() {
  return this.fi;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$UnitManifest$();
});
$p.r = (function(i) {
  this.hz(i);
});
$p.d = (function(v1) {
  this.hz((v1 | 0));
});
function $isArrayOf_sci_ArraySeq$ofUnit(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.ca)));
}
var $d_sci_ArraySeq$ofUnit = new $TypeData().i($c_sci_ArraySeq$ofUnit, "scala.collection.immutable.ArraySeq$ofUnit", ({
  ca: 1,
  a3: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a6: 1,
  a: 1
}));
function $p_sci_List__loop$2__I__I__sci_List__I($thiz, len$1, i, xs) {
  var xs$tailLocal1 = xs;
  var i$tailLocal1 = i;
  while (true) {
    if ((i$tailLocal1 === len$1)) {
      return ((!xs$tailLocal1.g()) | 0);
    } else {
      if ((!xs$tailLocal1.g())) {
        var i$tailLocal1$tmp1 = ((1 + i$tailLocal1) | 0);
        var xs$tailLocal1$tmp1 = xs$tailLocal1.o();
        i$tailLocal1 = i$tailLocal1$tmp1;
        xs$tailLocal1 = xs$tailLocal1$tmp1;
        continue;
      }
      return (-1);
    }
  }
}
function $p_sci_List__listEq$1__sci_List__sci_List__Z($thiz, a, b) {
  var b$tailLocal1 = b;
  var a$tailLocal1 = a;
  while (true) {
    if ((a$tailLocal1 === b$tailLocal1)) {
      return true;
    } else {
      var aEmpty = a$tailLocal1.g();
      var bEmpty = b$tailLocal1.g();
      if (((!(aEmpty || bEmpty)) && $m_sr_BoxesRunTime$().n(a$tailLocal1.m(), b$tailLocal1.m()))) {
        var a$tailLocal1$tmp1 = a$tailLocal1.o();
        var b$tailLocal1$tmp1 = b$tailLocal1.o();
        a$tailLocal1 = a$tailLocal1$tmp1;
        b$tailLocal1 = b$tailLocal1$tmp1;
        continue;
      }
      return (aEmpty && bEmpty);
    }
  }
}
/** @constructor */
function $c_sci_List() {
}
$p = $c_sci_List.prototype = new $h_sci_AbstractSeq();
$p.constructor = $c_sci_List;
/** @constructor */
function $h_sci_List() {
}
$h_sci_List.prototype = $p;
$p.kS = (function(x) {
  return $f_sc_LinearSeqOps__isDefinedAt__I__Z(this, x);
});
$p.r = (function(n) {
  return $f_sc_LinearSeqOps__apply__I__O(this, n);
});
$p.d4 = (function(z, op) {
  return $f_sc_LinearSeqOps__foldLeft__O__F2__O(this, z, op);
});
$p.f3 = (function(that) {
  return $f_sc_LinearSeqOps__sameElements__sc_IterableOnce__Z(this, that);
});
$p.dU = (function(p, from) {
  return $f_sc_LinearSeqOps__indexWhere__F1__I__I(this, p, from);
});
$p.bl = (function() {
  return "LinearSeq";
});
$p.c = (function() {
  return new $c_sc_StrictOptimizedLinearSeqOps$$anon$1(this);
});
$p.cb = (function(f) {
  return $f_sci_StrictOptimizedSeqOps__distinctBy__F1__O(this, f);
});
$p.bH = (function(ord) {
  return $f_sc_SeqOps__sorted__s_math_Ordering__O(this, ord);
});
$p.ep = (function() {
  return $m_sci_List$();
});
$p.nD = (function(prefix) {
  if (this.g()) {
    return prefix;
  } else if (prefix.g()) {
    return this;
  } else {
    var result = new $c_sci_$colon$colon(prefix.m(), this);
    var curr = result;
    var that = prefix.o();
    while ((!that.g())) {
      var temp = new $c_sci_$colon$colon(that.m(), this);
      curr.K = temp;
      curr = temp;
      that = that.o();
    }
    return result;
  }
});
$p.g = (function() {
  return (this === $m_sci_Nil$());
});
$p.cc = (function(prefix) {
  if ((prefix instanceof $c_sci_List)) {
    return this.nD(prefix);
  }
  if ((prefix.q() === 0)) {
    return this;
  }
  if ((prefix instanceof $c_scm_ListBuffer)) {
    if (this.g()) {
      return prefix.c2();
    }
  }
  var iter = prefix.c();
  if (iter.k()) {
    var result = new $c_sci_$colon$colon(iter.f(), this);
    var curr = result;
    while (iter.k()) {
      var temp = new $c_sci_$colon$colon(iter.f(), this);
      curr.K = temp;
      curr = temp;
    }
    return result;
  } else {
    return this;
  }
});
$p.pZ = (function(suffix) {
  return ((suffix instanceof $c_sci_List) ? suffix.nD(this) : $f_sc_StrictOptimizedSeqOps__appendedAll__sc_IterableOnce__O(this, suffix));
});
$p.oY = (function(n) {
  if ((this.g() || (n <= 0))) {
    var $x_1 = $m_sci_Nil$();
  } else {
    var h = new $c_sci_$colon$colon(this.m(), $m_sci_Nil$());
    var t = h;
    var rest = this.o();
    var i = 1;
    while (true) {
      if (rest.g()) {
        return this;
      }
      if ((i < n)) {
        i = ((1 + i) | 0);
        var nx = new $c_sci_$colon$colon(rest.m(), $m_sci_Nil$());
        t.K = nx;
        t = nx;
        rest = rest.o();
      } else {
        break;
      }
    }
    var $x_1 = h;
  }
  return $x_1;
});
$p.hN = (function(f) {
  if ((this === $m_sci_Nil$())) {
    var $x_1 = $m_sci_Nil$();
  } else {
    var h = new $c_sci_$colon$colon(f.d(this.m()), $m_sci_Nil$());
    var t = h;
    var rest = this.o();
    while ((rest !== $m_sci_Nil$())) {
      var nx = new $c_sci_$colon$colon(f.d(rest.m()), $m_sci_Nil$());
      t.K = nx;
      t = nx;
      rest = rest.o();
    }
    var $x_1 = h;
  }
  return $x_1;
});
$p.dO = (function(f) {
  var these = this;
  while ((!these.g())) {
    f.d(these.m());
    these = these.o();
  }
});
$p.l = (function() {
  var these = this;
  var len = 0;
  while ((!these.g())) {
    len = ((1 + len) | 0);
    these = these.o();
  }
  return len;
});
$p.aI = (function(len) {
  return ((len < 0) ? 1 : $p_sci_List__loop$2__I__I__sci_List__I(this, len, 0, this));
});
$p.fC = (function(p) {
  var these = this;
  while ((!these.g())) {
    if ((!(!p.d(these.m())))) {
      return true;
    }
    these = these.o();
  }
  return false;
});
$p.oy = (function() {
  if (this.g()) {
    throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), "List.last");
  } else {
    var these = this;
    var scout = this.o();
    while ((!scout.g())) {
      these = scout;
      scout = scout.o();
    }
    return these.m();
  }
});
$p.bK = (function() {
  return "List";
});
$p.c2 = (function() {
  return this;
});
$p.p = (function(o) {
  return ((o instanceof $c_sci_List) ? $p_sci_List__listEq$1__sci_List__sci_List__Z(this, this, o) : $f_sc_Seq__equals__O__Z(this, o));
});
$p.aH = (function() {
  return $m_sci_List$();
});
$p.bp = (function(elem) {
  return new $c_sci_$colon$colon(elem, this);
});
$p.aa = (function(f) {
  return this.hN(f);
});
$p.ky = (function(n) {
  return $p_sc_StrictOptimizedLinearSeqOps__loop$2__I__sc_LinearSeq__sc_LinearSeq(this, n, this);
});
$p.cF = (function(x) {
  return $f_sc_LinearSeqOps__isDefinedAt__I__Z(this, (x | 0));
});
$p.d = (function(v1) {
  return $f_sc_LinearSeqOps__apply__I__O(this, (v1 | 0));
});
function $isArrayOf_sci_List(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.b1)));
}
/** @constructor */
function $c_sci_VectorImpl() {
  this.e = null;
}
$p = $c_sci_VectorImpl.prototype = new $h_sci_Vector();
$p.constructor = $c_sci_VectorImpl;
/** @constructor */
function $h_sci_VectorImpl() {
}
$h_sci_VectorImpl.prototype = $p;
/** @constructor */
function $c_scm_ArraySeq$ofBoolean(array) {
  this.eN = null;
  this.eN = array;
}
$p = $c_scm_ArraySeq$ofBoolean.prototype = new $h_scm_ArraySeq();
$p.constructor = $c_scm_ArraySeq$ofBoolean;
/** @constructor */
function $h_scm_ArraySeq$ofBoolean() {
}
$h_scm_ArraySeq$ofBoolean.prototype = $p;
$p.l = (function() {
  return this.eN.a.length;
});
$p.hA = (function(index) {
  return this.eN.a[index];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.eN, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_scm_ArraySeq$ofBoolean) ? $m_ju_Arrays$().of(this.eN, that.eN) : $c_scm_ArraySeq.prototype.p.call(this, that));
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.eN);
});
$p.cD = (function() {
  return this.eN;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$BooleanManifest$();
});
$p.r = (function(i) {
  return this.hA(i);
});
$p.d = (function(v1) {
  return this.hA((v1 | 0));
});
function $isArrayOf_scm_ArraySeq$ofBoolean(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cu)));
}
var $d_scm_ArraySeq$ofBoolean = new $TypeData().i($c_scm_ArraySeq$ofBoolean, "scala.collection.mutable.ArraySeq$ofBoolean", ({
  cu: 1,
  a4: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  l: 1,
  u: 1,
  a: 1
}));
/** @constructor */
function $c_scm_ArraySeq$ofByte(array) {
  this.eO = null;
  this.eO = array;
}
$p = $c_scm_ArraySeq$ofByte.prototype = new $h_scm_ArraySeq();
$p.constructor = $c_scm_ArraySeq$ofByte;
/** @constructor */
function $h_scm_ArraySeq$ofByte() {
}
$h_scm_ArraySeq$ofByte.prototype = $p;
$p.l = (function() {
  return this.eO.a.length;
});
$p.hs = (function(index) {
  return this.eO.a[index];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.eO, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_scm_ArraySeq$ofByte) ? $m_ju_Arrays$().ob(this.eO, that.eO) : $c_scm_ArraySeq.prototype.p.call(this, that));
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.eO);
});
$p.cD = (function() {
  return this.eO;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$ByteManifest$();
});
$p.r = (function(i) {
  return this.hs(i);
});
$p.d = (function(v1) {
  return this.hs((v1 | 0));
});
function $isArrayOf_scm_ArraySeq$ofByte(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cv)));
}
var $d_scm_ArraySeq$ofByte = new $TypeData().i($c_scm_ArraySeq$ofByte, "scala.collection.mutable.ArraySeq$ofByte", ({
  cv: 1,
  a4: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  l: 1,
  u: 1,
  a: 1
}));
/** @constructor */
function $c_scm_ArraySeq$ofChar(array) {
  this.cB = null;
  this.cB = array;
}
$p = $c_scm_ArraySeq$ofChar.prototype = new $h_scm_ArraySeq();
$p.constructor = $c_scm_ArraySeq$ofChar;
/** @constructor */
function $h_scm_ArraySeq$ofChar() {
}
$h_scm_ArraySeq$ofChar.prototype = $p;
$p.l = (function() {
  return this.cB.a.length;
});
$p.ht = (function(index) {
  return this.cB.a[index];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.cB, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_scm_ArraySeq$ofChar) ? $m_ju_Arrays$().oc(this.cB, that.cB) : $c_scm_ArraySeq.prototype.p.call(this, that));
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.cB);
});
$p.cl = (function(sb, start, sep, end) {
  var jsb = sb.bC;
  if ((start.length !== 0)) {
    jsb.w = (("" + jsb.w) + start);
  }
  var len = this.cB.a.length;
  if ((len !== 0)) {
    if ((sep === "")) {
      jsb.nO(this.cB);
    } else {
      jsb.l();
      var c = this.cB.a[0];
      var str = ("" + $cToS(c));
      jsb.w = (jsb.w + str);
      var i = 1;
      while ((i < len)) {
        jsb.w = (("" + jsb.w) + sep);
        var c$1 = this.cB.a[i];
        var str$1 = ("" + $cToS(c$1));
        jsb.w = (jsb.w + str$1);
        i = ((1 + i) | 0);
      }
    }
  }
  if ((end.length !== 0)) {
    jsb.w = (("" + jsb.w) + end);
  }
  return sb;
});
$p.cD = (function() {
  return this.cB;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$CharManifest$();
});
$p.r = (function(i) {
  return $bC(this.ht(i));
});
$p.d = (function(v1) {
  return $bC(this.ht((v1 | 0)));
});
function $isArrayOf_scm_ArraySeq$ofChar(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cw)));
}
var $d_scm_ArraySeq$ofChar = new $TypeData().i($c_scm_ArraySeq$ofChar, "scala.collection.mutable.ArraySeq$ofChar", ({
  cw: 1,
  a4: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  l: 1,
  u: 1,
  a: 1
}));
/** @constructor */
function $c_scm_ArraySeq$ofDouble(array) {
  this.cY = null;
  this.cY = array;
}
$p = $c_scm_ArraySeq$ofDouble.prototype = new $h_scm_ArraySeq();
$p.constructor = $c_scm_ArraySeq$ofDouble;
/** @constructor */
function $h_scm_ArraySeq$ofDouble() {
}
$h_scm_ArraySeq$ofDouble.prototype = $p;
$p.l = (function() {
  return this.cY.a.length;
});
$p.hu = (function(index) {
  return this.cY.a[index];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.cY, this$1.aY);
});
$p.p = (function(that) {
  if ((that instanceof $c_scm_ArraySeq$ofDouble)) {
    var thatArray = that.cY;
    if ((this.cY === thatArray)) {
      return true;
    } else if ((this.cY.a.length === thatArray.a.length)) {
      var i = 0;
      while (((i < this.cY.a.length) && (this.cY.a[i] === thatArray.a[i]))) {
        i = ((1 + i) | 0);
      }
      return (i >= this.cY.a.length);
    } else {
      return false;
    }
  } else {
    return $c_scm_ArraySeq.prototype.p.call(this, that);
  }
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.cY);
});
$p.cD = (function() {
  return this.cY;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$DoubleManifest$();
});
$p.r = (function(i) {
  return this.hu(i);
});
$p.d = (function(v1) {
  return this.hu((v1 | 0));
});
function $isArrayOf_scm_ArraySeq$ofDouble(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cx)));
}
var $d_scm_ArraySeq$ofDouble = new $TypeData().i($c_scm_ArraySeq$ofDouble, "scala.collection.mutable.ArraySeq$ofDouble", ({
  cx: 1,
  a4: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  l: 1,
  u: 1,
  a: 1
}));
/** @constructor */
function $c_scm_ArraySeq$ofFloat(array) {
  this.cZ = null;
  this.cZ = array;
}
$p = $c_scm_ArraySeq$ofFloat.prototype = new $h_scm_ArraySeq();
$p.constructor = $c_scm_ArraySeq$ofFloat;
/** @constructor */
function $h_scm_ArraySeq$ofFloat() {
}
$h_scm_ArraySeq$ofFloat.prototype = $p;
$p.l = (function() {
  return this.cZ.a.length;
});
$p.hv = (function(index) {
  return this.cZ.a[index];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.cZ, this$1.aY);
});
$p.p = (function(that) {
  if ((that instanceof $c_scm_ArraySeq$ofFloat)) {
    var thatArray = that.cZ;
    if ((this.cZ === thatArray)) {
      return true;
    } else if ((this.cZ.a.length === thatArray.a.length)) {
      var i = 0;
      while (((i < this.cZ.a.length) && (this.cZ.a[i] === thatArray.a[i]))) {
        i = ((1 + i) | 0);
      }
      return (i >= this.cZ.a.length);
    } else {
      return false;
    }
  } else {
    return $c_scm_ArraySeq.prototype.p.call(this, that);
  }
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.cZ);
});
$p.cD = (function() {
  return this.cZ;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$FloatManifest$();
});
$p.r = (function(i) {
  return this.hv(i);
});
$p.d = (function(v1) {
  return this.hv((v1 | 0));
});
function $isArrayOf_scm_ArraySeq$ofFloat(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cy)));
}
var $d_scm_ArraySeq$ofFloat = new $TypeData().i($c_scm_ArraySeq$ofFloat, "scala.collection.mutable.ArraySeq$ofFloat", ({
  cy: 1,
  a4: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  l: 1,
  u: 1,
  a: 1
}));
/** @constructor */
function $c_scm_ArraySeq$ofInt(array) {
  this.eP = null;
  this.eP = array;
}
$p = $c_scm_ArraySeq$ofInt.prototype = new $h_scm_ArraySeq();
$p.constructor = $c_scm_ArraySeq$ofInt;
/** @constructor */
function $h_scm_ArraySeq$ofInt() {
}
$h_scm_ArraySeq$ofInt.prototype = $p;
$p.l = (function() {
  return this.eP.a.length;
});
$p.hw = (function(index) {
  return this.eP.a[index];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.eP, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_scm_ArraySeq$ofInt) ? $m_ju_Arrays$().iS(this.eP, that.eP) : $c_scm_ArraySeq.prototype.p.call(this, that));
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.eP);
});
$p.cD = (function() {
  return this.eP;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$IntManifest$();
});
$p.r = (function(i) {
  return this.hw(i);
});
$p.d = (function(v1) {
  return this.hw((v1 | 0));
});
function $isArrayOf_scm_ArraySeq$ofInt(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cz)));
}
var $d_scm_ArraySeq$ofInt = new $TypeData().i($c_scm_ArraySeq$ofInt, "scala.collection.mutable.ArraySeq$ofInt", ({
  cz: 1,
  a4: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  l: 1,
  u: 1,
  a: 1
}));
/** @constructor */
function $c_scm_ArraySeq$ofLong(array) {
  this.eQ = null;
  this.eQ = array;
}
$p = $c_scm_ArraySeq$ofLong.prototype = new $h_scm_ArraySeq();
$p.constructor = $c_scm_ArraySeq$ofLong;
/** @constructor */
function $h_scm_ArraySeq$ofLong() {
}
$h_scm_ArraySeq$ofLong.prototype = $p;
$p.l = (function() {
  return ((this.eQ.a.length >>> 1) | 0);
});
$p.hx = (function(index) {
  var $x_1 = this.eQ.a;
  var $x_2 = (index << 1);
  return $bL($x_1[$x_2], $x_1[(($x_2 + 1) | 0)]);
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.eQ, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_scm_ArraySeq$ofLong) ? $m_ju_Arrays$().od(this.eQ, that.eQ) : $c_scm_ArraySeq.prototype.p.call(this, that));
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.eQ);
});
$p.cD = (function() {
  return this.eQ;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$LongManifest$();
});
$p.r = (function(i) {
  return this.hx(i);
});
$p.d = (function(v1) {
  return this.hx((v1 | 0));
});
function $isArrayOf_scm_ArraySeq$ofLong(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cA)));
}
var $d_scm_ArraySeq$ofLong = new $TypeData().i($c_scm_ArraySeq$ofLong, "scala.collection.mutable.ArraySeq$ofLong", ({
  cA: 1,
  a4: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  l: 1,
  u: 1,
  a: 1
}));
/** @constructor */
function $c_scm_ArraySeq$ofRef(array) {
  this.ee = null;
  this.ee = array;
}
$p = $c_scm_ArraySeq$ofRef.prototype = new $h_scm_ArraySeq();
$p.constructor = $c_scm_ArraySeq$ofRef;
/** @constructor */
function $h_scm_ArraySeq$ofRef() {
}
$h_scm_ArraySeq$ofRef.prototype = $p;
$p.aS = (function() {
  return $m_s_reflect_ClassTag$().kn($objectGetClass(this.ee).J.Q());
});
$p.l = (function() {
  return this.ee.a.length;
});
$p.r = (function(index) {
  return this.ee.a[index];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.ee, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_scm_ArraySeq$ofRef) ? $m_s_Array$().og(this.ee, that.ee) : $c_scm_ArraySeq.prototype.p.call(this, that));
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.ee);
});
$p.cD = (function() {
  return this.ee;
});
$p.d = (function(v1) {
  return this.r((v1 | 0));
});
function $isArrayOf_scm_ArraySeq$ofRef(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cB)));
}
var $d_scm_ArraySeq$ofRef = new $TypeData().i($c_scm_ArraySeq$ofRef, "scala.collection.mutable.ArraySeq$ofRef", ({
  cB: 1,
  a4: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  l: 1,
  u: 1,
  a: 1
}));
/** @constructor */
function $c_scm_ArraySeq$ofShort(array) {
  this.eR = null;
  this.eR = array;
}
$p = $c_scm_ArraySeq$ofShort.prototype = new $h_scm_ArraySeq();
$p.constructor = $c_scm_ArraySeq$ofShort;
/** @constructor */
function $h_scm_ArraySeq$ofShort() {
}
$h_scm_ArraySeq$ofShort.prototype = $p;
$p.l = (function() {
  return this.eR.a.length;
});
$p.hy = (function(index) {
  return this.eR.a[index];
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.eR, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_scm_ArraySeq$ofShort) ? $m_ju_Arrays$().oe(this.eR, that.eR) : $c_scm_ArraySeq.prototype.p.call(this, that));
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.eR);
});
$p.cD = (function() {
  return this.eR;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$ShortManifest$();
});
$p.r = (function(i) {
  return this.hy(i);
});
$p.d = (function(v1) {
  return this.hy((v1 | 0));
});
function $isArrayOf_scm_ArraySeq$ofShort(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cC)));
}
var $d_scm_ArraySeq$ofShort = new $TypeData().i($c_scm_ArraySeq$ofShort, "scala.collection.mutable.ArraySeq$ofShort", ({
  cC: 1,
  a4: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  l: 1,
  u: 1,
  a: 1
}));
/** @constructor */
function $c_scm_ArraySeq$ofUnit(array) {
  this.fw = null;
  this.fw = array;
}
$p = $c_scm_ArraySeq$ofUnit.prototype = new $h_scm_ArraySeq();
$p.constructor = $c_scm_ArraySeq$ofUnit;
/** @constructor */
function $h_scm_ArraySeq$ofUnit() {
}
$h_scm_ArraySeq$ofUnit.prototype = $p;
$p.l = (function() {
  return this.fw.a.length;
});
$p.hz = (function(index) {
});
$p.u = (function() {
  var this$1 = $m_s_util_hashing_MurmurHash3$();
  return this$1.bo(this.fw, this$1.aY);
});
$p.p = (function(that) {
  return ((that instanceof $c_scm_ArraySeq$ofUnit) ? (this.fw.a.length === that.fw.a.length) : $c_scm_ArraySeq.prototype.p.call(this, that));
});
$p.c = (function() {
  return new $c_sc_ArrayOps$ArrayIterator(this.fw);
});
$p.cD = (function() {
  return this.fw;
});
$p.aS = (function() {
  return $m_s_reflect_ManifestFactory$UnitManifest$();
});
$p.r = (function(i) {
  this.hz(i);
});
$p.d = (function(v1) {
  this.hz((v1 | 0));
});
function $isArrayOf_scm_ArraySeq$ofUnit(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cD)));
}
var $d_scm_ArraySeq$ofUnit = new $TypeData().i($c_scm_ArraySeq$ofUnit, "scala.collection.mutable.ArraySeq$ofUnit", ({
  cD: 1,
  a4: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  l: 1,
  u: 1,
  a: 1
}));
function $ct_scm_HashMap__I__D__($thiz, initialCapacity, loadFactor) {
  $thiz.k1 = loadFactor;
  $thiz.Q = new ($d_scm_HashMap$Node.r().C)($p_scm_HashMap__tableSizeFor__I__I($thiz, initialCapacity));
  $thiz.hb = $p_scm_HashMap__newThreshold__I__I($thiz, $thiz.Q.a.length);
  $thiz.d0 = 0;
  return $thiz;
}
function $ct_scm_HashMap__($thiz) {
  $ct_scm_HashMap__I__D__($thiz, 16, 0.75);
  return $thiz;
}
function $p_scm_HashMap__put0__O__O__I__Z__s_Some($thiz, key, value, hash, getOld) {
  if ((((1 + $thiz.d0) | 0) >= $thiz.hb)) {
    $p_scm_HashMap__growTable__I__V($thiz, ($thiz.Q.a.length << 1));
  }
  return $p_scm_HashMap__put0__O__O__Z__I__I__s_Some($thiz, key, value, getOld, hash, (hash & (($thiz.Q.a.length - 1) | 0)));
}
function $p_scm_HashMap__put0__O__O__Z__s_Some($thiz, key, value, getOld) {
  if ((((1 + $thiz.d0) | 0) >= $thiz.hb)) {
    $p_scm_HashMap__growTable__I__V($thiz, ($thiz.Q.a.length << 1));
  }
  var originalHash = $m_sr_Statics$().y(key);
  var hash = (originalHash ^ ((originalHash >>> 16) | 0));
  return $p_scm_HashMap__put0__O__O__Z__I__I__s_Some($thiz, key, value, getOld, hash, (hash & (($thiz.Q.a.length - 1) | 0)));
}
function $p_scm_HashMap__put0__O__O__Z__I__I__s_Some($thiz, key, value, getOld, hash, idx) {
  matchResult7: {
    var x30 = $thiz.Q.a[idx];
    if ((x30 === null)) {
      $thiz.Q.a[idx] = new $c_scm_HashMap$Node(key, hash, value, null);
      break matchResult7;
    }
    var prev = null;
    var n = x30;
    while (((n !== null) && (n.dK <= hash))) {
      if (((n.dK === hash) && $m_sr_BoxesRunTime$().n(key, n.eT))) {
        var old$2 = n.cC;
        var this$2 = n;
        this$2.cC = value;
        return (getOld ? new $c_s_Some(old$2) : null);
      }
      prev = n;
      n = n.bh;
    }
    if ((prev === null)) {
      $thiz.Q.a[idx] = new $c_scm_HashMap$Node(key, hash, value, x30);
    } else {
      var this$6 = prev;
      var n$1 = new $c_scm_HashMap$Node(key, hash, value, prev.bh);
      this$6.bh = n$1;
    }
  }
  $thiz.d0 = ((1 + $thiz.d0) | 0);
  return null;
}
function $p_scm_HashMap__growTable__I__V($thiz, newlen) {
  if ((newlen < 0)) {
    throw $ct_jl_RuntimeException__T__(new $c_jl_RuntimeException(), (("new HashMap table size " + newlen) + " exceeds maximum"));
  }
  var oldlen = $thiz.Q.a.length;
  $thiz.hb = $p_scm_HashMap__newThreshold__I__I($thiz, newlen);
  if (($thiz.d0 === 0)) {
    $thiz.Q = new ($d_scm_HashMap$Node.r().C)(newlen);
  } else {
    $thiz.Q = $m_ju_Arrays$().a9($thiz.Q, newlen);
    var preLow = new $c_scm_HashMap$Node(null, 0, null, null);
    var preHigh = new $c_scm_HashMap$Node(null, 0, null, null);
    while ((oldlen < newlen)) {
      var i = 0;
      while ((i < oldlen)) {
        var old = $thiz.Q.a[i];
        if ((old !== null)) {
          preLow.bh = null;
          preHigh.bh = null;
          var lastLow = preLow;
          var lastHigh = preHigh;
          var n = old;
          while ((n !== null)) {
            var next = n.bh;
            if (((n.dK & oldlen) === 0)) {
              var this$2 = lastLow;
              var n$1 = n;
              this$2.bh = n$1;
              lastLow = n;
            } else {
              var this$3 = lastHigh;
              var n$2 = n;
              this$3.bh = n$2;
              lastHigh = n;
            }
            n = next;
          }
          var this$4 = lastLow;
          this$4.bh = null;
          if ((old !== preLow.bh)) {
            $thiz.Q.a[i] = preLow.bh;
          }
          if ((preHigh.bh !== null)) {
            $thiz.Q.a[((i + oldlen) | 0)] = preHigh.bh;
            var this$5 = lastHigh;
            this$5.bh = null;
          }
        }
        i = ((1 + i) | 0);
      }
      oldlen = (oldlen << 1);
    }
  }
}
function $p_scm_HashMap__tableSizeFor__I__I($thiz, capacity) {
  var x = ((capacity - 1) | 0);
  var i = ((x > 4) ? x : 4);
  var x$1 = ((((-2147483648) >> Math.clz32(i)) & i) << 1);
  return ((x$1 < 1073741824) ? x$1 : 1073741824);
}
function $p_scm_HashMap__newThreshold__I__I($thiz, size) {
  return $doubleToInt((size * $thiz.k1));
}
/** @constructor */
function $c_scm_HashMap() {
  this.k1 = 0.0;
  this.Q = null;
  this.hb = 0;
  this.d0 = 0;
}
$p = $c_scm_HashMap.prototype = new $h_scm_AbstractMap();
$p.constructor = $c_scm_HashMap;
/** @constructor */
function $h_scm_HashMap() {
}
$h_scm_HashMap.prototype = $p;
$p.M = (function() {
  return this.d0;
});
$p.bb = (function(key) {
  var originalHash = $m_sr_Statics$().y(key);
  var hash = (originalHash ^ ((originalHash >>> 16) | 0));
  matchResult1: {
    var $x_1;
    var x1 = this.Q.a[(hash & ((this.Q.a.length - 1) | 0))];
    if ((x1 === null)) {
      var $x_1 = null;
      break matchResult1;
    }
    var $x_1 = x1.hH(key, hash);
  }
  return ($x_1 !== null);
});
$p.aZ = (function(size) {
  var target = $p_scm_HashMap__tableSizeFor__I__I(this, $doubleToInt((((1 + size) | 0) / this.k1)));
  if ((target > this.Q.a.length)) {
    $p_scm_HashMap__growTable__I__V(this, target);
  }
});
$p.nJ = (function(xs) {
  $f_scm_Builder__sizeHint__sc_IterableOnce__I__V(this, xs, 0);
  if ((xs instanceof $c_sci_HashMap)) {
    var f = new $c_sr_AbstractFunction3_$$Lambda$d1e06cbab540de4f9f09e7182f18ea80659b9825(((v1$2, v2$2, v3$2) => {
      var h = (v3$2 | 0);
      $p_scm_HashMap__put0__O__O__I__Z__s_Some(this, v1$2, v2$2, (h ^ ((h >>> 16) | 0)), false);
    }));
    xs.bN.kG(f);
    return this;
  } else if ((xs instanceof $c_scm_HashMap)) {
    var iter = xs.oB();
    while (iter.k()) {
      var next = iter.f();
      $p_scm_HashMap__put0__O__O__I__Z__s_Some(this, next.eT, next.cC, next.dK, false);
    }
    return this;
  } else if (false) {
    var iter$2 = xs.oa();
    while (iter$2.k()) {
      var entry = iter$2.f();
      $p_scm_HashMap__put0__O__O__I__Z__s_Some(this, entry.ox(), entry.p7(), entry.or(), false);
    }
    return this;
  } else {
    return ($is_scm_Map(xs) ? (xs.dm(new $c_sr_AbstractFunction2_$$Lambda$b4228bd32034ae3b2f0c5fc896319aa4b79b55f8(((key$2, value$2) => {
      var originalHash = $m_sr_Statics$().y(key$2);
      return $p_scm_HashMap__put0__O__O__I__Z__s_Some(this, key$2, value$2, (originalHash ^ ((originalHash >>> 16) | 0)), false);
    }))), this) : $f_scm_Growable__addAll__sc_IterableOnce__scm_Growable(this, xs));
  }
});
$p.c = (function() {
  return ((this.d0 === 0) ? $m_sc_Iterator$().G : new $c_scm_HashMap$$anon$1(this));
});
$p.oB = (function() {
  return ((this.d0 === 0) ? $m_sc_Iterator$().G : new $c_scm_HashMap$$anon$4(this));
});
$p.cn = (function(key) {
  var originalHash = $m_sr_Statics$().y(key);
  var hash = (originalHash ^ ((originalHash >>> 16) | 0));
  matchResult1: {
    var x34;
    var x1 = this.Q.a[(hash & ((this.Q.a.length - 1) | 0))];
    if ((x1 === null)) {
      var x34 = null;
      break matchResult1;
    }
    var x34 = x1.hH(key, hash);
  }
  if ((x34 === null)) {
    return $m_s_None$();
  }
  return new $c_s_Some(x34.cC);
});
$p.d = (function(key) {
  var originalHash = $m_sr_Statics$().y(key);
  var hash = (originalHash ^ ((originalHash >>> 16) | 0));
  matchResult1: {
    var x35;
    var x1 = this.Q.a[(hash & ((this.Q.a.length - 1) | 0))];
    if ((x1 === null)) {
      var x35 = null;
      break matchResult1;
    }
    var x35 = x1.hH(key, hash);
  }
  if ((x35 === null)) {
    return $f_sc_MapOps__default__O__O(this, key);
  }
  return x35.cC;
});
$p.eo = (function(key, default$1) {
  if ((!($objectGetClass(this) === $d_scm_HashMap.l()))) {
    return $f_sc_MapOps__getOrElse__O__F0__O(this, key, default$1);
  } else {
    var originalHash = $m_sr_Statics$().y(key);
    var hash = (originalHash ^ ((originalHash >>> 16) | 0));
    matchResult1: {
      var nd;
      var x1 = this.Q.a[(hash & ((this.Q.a.length - 1) | 0))];
      if ((x1 === null)) {
        var nd = null;
        break matchResult1;
      }
      var nd = x1.hH(key, hash);
    }
    return ((nd === null) ? default$1.a2() : nd.cC);
  }
});
$p.r3 = (function(key, defaultValue) {
  if ((!($objectGetClass(this) === $d_scm_HashMap.l()))) {
    return $f_scm_MapOps__getOrElseUpdate__O__F0__O(this, key, defaultValue);
  } else {
    var originalHash = $m_sr_Statics$().y(key);
    var hash = (originalHash ^ ((originalHash >>> 16) | 0));
    var idx = (hash & ((this.Q.a.length - 1) | 0));
    matchResult13: {
      var nd$2;
      var x36 = this.Q.a[idx];
      if ((x36 === null)) {
        var nd$2 = null;
        break matchResult13;
      }
      var nd$2 = x36.hH(key, hash);
    }
    if ((nd$2 !== null)) {
      return nd$2.cC;
    } else {
      var table0 = this.Q;
      var default$1 = defaultValue.a2();
      if ((((1 + this.d0) | 0) >= this.hb)) {
        $p_scm_HashMap__growTable__I__V(this, (this.Q.a.length << 1));
      }
      $p_scm_HashMap__put0__O__O__Z__I__I__s_Some(this, key, default$1, false, hash, ((table0 === this.Q) ? idx : (hash & ((this.Q.a.length - 1) | 0))));
      return default$1;
    }
  }
});
$p.sp = (function(key, value) {
  $p_scm_HashMap__put0__O__O__Z__s_Some(this, key, value, false);
});
$p.pW = (function(elem) {
  $p_scm_HashMap__put0__O__O__Z__s_Some(this, elem.aw(), elem.ap(), false);
  return this;
});
$p.q = (function() {
  return this.d0;
});
$p.g = (function() {
  return (this.d0 === 0);
});
$p.dm = (function(f) {
  var len = this.Q.a.length;
  var i = 0;
  while ((i < len)) {
    var n = this.Q.a[i];
    if ((n !== null)) {
      n.dm(f);
    }
    i = ((1 + i) | 0);
  }
});
$p.j1 = (function() {
  return $m_scm_HashMap$();
});
$p.bl = (function() {
  return "HashMap";
});
$p.u = (function() {
  if (this.g()) {
    return $m_s_util_hashing_MurmurHash3$().iH;
  } else {
    var tupleHashIterator = new $c_scm_HashMap$$anon$5(this);
    return $m_s_util_hashing_MurmurHash3$().gA(tupleHashIterator, $m_s_util_hashing_MurmurHash3$().el);
  }
});
$p.b7 = (function(elems) {
  return this.nJ(elems);
});
$p.aq = (function(elem) {
  return this.pW(elem);
});
function $isArrayOf_scm_HashMap(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cE)));
}
var $d_scm_HashMap = new $TypeData().i($c_scm_HashMap, "scala.collection.mutable.HashMap", ({
  cE: 1,
  gH: 1,
  ak: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  a9: 1,
  an: 1,
  d: 1,
  a8: 1,
  N: 1,
  E: 1,
  M: 1,
  H: 1,
  I: 1,
  K: 1,
  aG: 1,
  hf: 1,
  b7: 1,
  l: 1,
  bY: 1,
  a: 1
}));
function $ct_sci_BigVector__AO__AO__I__($thiz, _prefix1, suffix1, length0) {
  $thiz.i = suffix1;
  $thiz.j = length0;
  $ct_sci_Vector__AO__($thiz, _prefix1);
  return $thiz;
}
/** @constructor */
function $c_sci_BigVector() {
  this.e = null;
  this.i = null;
  this.j = 0;
}
$p = $c_sci_BigVector.prototype = new $h_sci_VectorImpl();
$p.constructor = $c_sci_BigVector;
/** @constructor */
function $h_sci_BigVector() {
}
$h_sci_BigVector.prototype = $p;
function $isArrayOf_sci_BigVector(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.ap)));
}
/** @constructor */
function $c_sci_Vector1(_data1) {
  this.e = null;
  $ct_sci_Vector__AO__(this, _data1);
}
$p = $c_sci_Vector1.prototype = new $h_sci_VectorImpl();
$p.constructor = $c_sci_Vector1;
/** @constructor */
function $h_sci_Vector1() {
}
$h_sci_Vector1.prototype = $p;
$p.r = (function(index) {
  if (((index >= 0) && (index < this.e.a.length))) {
    return this.e.a[index];
  } else {
    throw this.bi(index);
  }
});
$p.er = (function(index, elem) {
  if (((index >= 0) && (index < this.e.a.length))) {
    var a1 = this.e;
    var a1c = a1.h();
    a1c.a[index] = elem;
    return new $c_sci_Vector1(a1c);
  } else {
    throw this.bi(index);
  }
});
$p.dM = (function(elem) {
  if ((this.e.a.length < 32)) {
    return new $c_sci_Vector1($m_sci_VectorStatics$().go(this.e, elem));
  } else {
    var $x_2 = this.e;
    var $x_1 = $m_sci_VectorStatics$().ao;
    var a = new $ac_O(1);
    a.a[0] = elem;
    return new $c_sci_Vector2($x_2, 32, $x_1, a, 33);
  }
});
$p.cJ = (function(elem) {
  var len1 = this.e.a.length;
  if ((len1 < 32)) {
    return new $c_sci_Vector1($m_sci_VectorStatics$().gp(elem, this.e));
  } else {
    var a = new $ac_O(1);
    a.a[0] = elem;
    return new $c_sci_Vector2(a, 1, $m_sci_VectorStatics$().ao, this.e, ((1 + len1) | 0));
  }
});
$p.dq = (function(f) {
  return new $c_sci_Vector1($m_sci_VectorStatics$().d5(this.e, f));
});
$p.dY = (function() {
  return 1;
});
$p.dX = (function(idx) {
  return this.e;
});
$p.d = (function(v1) {
  var index = (v1 | 0);
  if (((index >= 0) && (index < this.e.a.length))) {
    return this.e.a[index];
  } else {
    throw this.bi(index);
  }
});
$p.bp = (function(elem) {
  return this.cJ(elem);
});
$p.aa = (function(f) {
  return this.dq(f);
});
var $d_sci_Vector1 = new $TypeData().i($c_sci_Vector1, "scala.collection.immutable.Vector1", ({
  gx: 1,
  aq: 1,
  ag: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a: 1,
  X: 1
}));
/** @constructor */
function $c_sci_$colon$colon(head, next) {
  this.gb = null;
  this.K = null;
  this.gb = head;
  this.K = next;
}
$p = $c_sci_$colon$colon.prototype = new $h_sci_List();
$p.constructor = $c_sci_$colon$colon;
/** @constructor */
function $h_sci_$colon$colon() {
}
$h_sci_$colon$colon.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.bE = (function() {
  return 2;
});
$p.bG = (function() {
  return "::";
});
$p.bF = (function(n) {
  if ((n === 0)) {
    return this.gb;
  }
  if ((n === 1)) {
    return this.K;
  }
  throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
});
$p.m = (function() {
  return this.gb;
});
$p.o = (function() {
  return this.K;
});
function $isArrayOf_sci_$colon$colon(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.c0)));
}
var $d_sci_$colon$colon = new $TypeData().i($c_sci_$colon$colon, "scala.collection.immutable.$colon$colon", ({
  c0: 1,
  b1: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  aX: 1,
  aB: 1,
  b0: 1,
  aZ: 1,
  l: 1,
  u: 1,
  bX: 1,
  D: 1,
  a: 1,
  X: 1,
  J: 1
}));
/** @constructor */
function $c_sci_Nil$() {
  $n_sci_Nil$ = this;
  var _1 = $m_sci_Nil$();
  $m_sci_Nil$();
}
$p = $c_sci_Nil$.prototype = new $h_sci_List();
$p.constructor = $c_sci_Nil$;
/** @constructor */
function $h_sci_Nil$() {
}
$h_sci_Nil$.prototype = $p;
$p.cd = (function() {
  return new $c_s_Product$$anon$1(this);
});
$p.bE = (function() {
  return 0;
});
$p.bG = (function() {
  return "Nil";
});
$p.bF = (function(n) {
  throw $ct_jl_IndexOutOfBoundsException__I__(new $c_jl_IndexOutOfBoundsException(), n);
});
$p.r8 = (function() {
  throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), "head of empty list");
});
$p.sg = (function() {
  throw new $c_jl_UnsupportedOperationException("tail of empty list");
});
$p.rn = (function() {
  throw $ct_ju_NoSuchElementException__T__(new $c_ju_NoSuchElementException(), "last of empty list");
});
$p.q = (function() {
  return 0;
});
$p.c = (function() {
  return $m_sc_Iterator$().G;
});
$p.m = (function() {
  this.r8();
});
$p.o = (function() {
  this.sg();
});
$p.oy = (function() {
  this.rn();
});
var $d_sci_Nil$ = new $TypeData().i($c_sci_Nil$, "scala.collection.immutable.Nil$", ({
  gc: 1,
  b1: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  aX: 1,
  aB: 1,
  b0: 1,
  aZ: 1,
  l: 1,
  u: 1,
  bX: 1,
  D: 1,
  a: 1,
  X: 1,
  J: 1
}));
var $n_sci_Nil$;
function $m_sci_Nil$() {
  if ((!$n_sci_Nil$)) {
    $n_sci_Nil$ = new $c_sci_Nil$();
  }
  return $n_sci_Nil$;
}
/** @constructor */
function $c_sci_Vector0$() {
  this.e = null;
  this.i = null;
  this.j = 0;
  $ct_sci_BigVector__AO__AO__I__(this, $m_sci_VectorStatics$().jV, $m_sci_VectorStatics$().jV, 0);
}
$p = $c_sci_Vector0$.prototype = new $h_sci_BigVector();
$p.constructor = $c_sci_Vector0$;
/** @constructor */
function $h_sci_Vector0$() {
}
$h_sci_Vector0$.prototype = $p;
$p.nQ = (function(index) {
  throw this.bi(index);
});
$p.er = (function(index, elem) {
  throw this.bi(index);
});
$p.dM = (function(elem) {
  var a = new $ac_O(1);
  a.a[0] = elem;
  return new $c_sci_Vector1(a);
});
$p.cJ = (function(elem) {
  var a = new $ac_O(1);
  a.a[0] = elem;
  return new $c_sci_Vector1(a);
});
$p.dY = (function() {
  return 0;
});
$p.dX = (function(idx) {
  return null;
});
$p.p = (function(o) {
  return ((this === o) || ((o instanceof $c_sci_Vector) ? false : $f_sc_Seq__equals__O__Z(this, o)));
});
$p.bi = (function(index) {
  return $ct_jl_IndexOutOfBoundsException__T__(new $c_jl_IndexOutOfBoundsException(), (index + " is out of bounds (empty vector)"));
});
$p.r = (function(i) {
  this.nQ(i);
});
$p.d = (function(v1) {
  this.nQ((v1 | 0));
});
$p.bp = (function(elem) {
  return this.cJ(elem);
});
$p.aa = (function(f) {
  return this;
});
var $d_sci_Vector0$ = new $TypeData().i($c_sci_Vector0$, "scala.collection.immutable.Vector0$", ({
  gw: 1,
  ap: 1,
  aq: 1,
  ag: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a: 1,
  X: 1
}));
var $n_sci_Vector0$;
function $m_sci_Vector0$() {
  if ((!$n_sci_Vector0$)) {
    $n_sci_Vector0$ = new $c_sci_Vector0$();
  }
  return $n_sci_Vector0$;
}
/** @constructor */
function $c_sci_Vector2(_prefix1, len1, data2, _suffix1, _length0) {
  this.e = null;
  this.i = null;
  this.j = 0;
  this.bP = 0;
  this.bn = null;
  this.bP = len1;
  this.bn = data2;
  $ct_sci_BigVector__AO__AO__I__(this, _prefix1, _suffix1, _length0);
}
$p = $c_sci_Vector2.prototype = new $h_sci_BigVector();
$p.constructor = $c_sci_Vector2;
/** @constructor */
function $h_sci_Vector2() {
}
$h_sci_Vector2.prototype = $p;
$p.r = (function(index) {
  if (((index >= 0) && (index < this.j))) {
    var io = ((index - this.bP) | 0);
    if ((io >= 0)) {
      var i2 = ((io >>> 5) | 0);
      var i1 = (31 & io);
      return ((i2 < this.bn.a.length) ? this.bn.a[i2].a[i1] : this.i.a[(31 & io)]);
    } else {
      return this.e.a[index];
    }
  } else {
    throw this.bi(index);
  }
});
$p.er = (function(index, elem) {
  if (((index >= 0) && (index < this.j))) {
    if ((index >= this.bP)) {
      var io = ((index - this.bP) | 0);
      var i2 = ((io >>> 5) | 0);
      var i1 = (31 & io);
      if ((i2 < this.bn.a.length)) {
        var a2 = this.bn;
        var a2c = a2.h();
        var a1 = a2c.a[i2];
        var a1c = a1.h();
        a1c.a[i1] = elem;
        a2c.a[i2] = a1c;
        return new $c_sci_Vector2(this.e, this.bP, a2c, this.i, this.j);
      } else {
        var a1$1 = this.i;
        var a1c$1 = a1$1.h();
        a1c$1.a[i1] = elem;
        return new $c_sci_Vector2(this.e, this.bP, this.bn, a1c$1, this.j);
      }
    } else {
      var a1$2 = this.e;
      var a1c$2 = a1$2.h();
      a1c$2.a[index] = elem;
      return new $c_sci_Vector2(a1c$2, this.bP, this.bn, this.i, this.j);
    }
  } else {
    throw this.bi(index);
  }
});
$p.dM = (function(elem) {
  if ((this.i.a.length < 32)) {
    var suffix1$3 = $m_sci_VectorStatics$().go(this.i, elem);
    var length0$3 = ((1 + this.j) | 0);
    return new $c_sci_Vector2(this.e, this.bP, this.bn, suffix1$3, length0$3);
  } else if ((this.bn.a.length < 30)) {
    var data2$4 = $m_sci_VectorStatics$().F(this.bn, this.i);
    var a = new $ac_O(1);
    a.a[0] = elem;
    var length0$4 = ((1 + this.j) | 0);
    return new $c_sci_Vector2(this.e, this.bP, data2$4, a, length0$4);
  } else {
    var $x_5 = this.e;
    var $x_4 = this.bP;
    var $x_3 = this.bn;
    var $x_2 = this.bP;
    var $x_1 = $m_sci_VectorStatics$().by;
    var x = this.i;
    var a$1 = new ($d_O.r().r().C)(1);
    a$1.a[0] = x;
    var a$2 = new $ac_O(1);
    a$2.a[0] = elem;
    return new $c_sci_Vector3($x_5, $x_4, $x_3, ((960 + $x_2) | 0), $x_1, a$1, a$2, ((1 + this.j) | 0));
  }
});
$p.cJ = (function(elem) {
  if ((this.bP < 32)) {
    var prefix1$5 = $m_sci_VectorStatics$().gp(elem, this.e);
    var len1$5 = ((1 + this.bP) | 0);
    var length0$5 = ((1 + this.j) | 0);
    return new $c_sci_Vector2(prefix1$5, len1$5, this.bn, this.i, length0$5);
  } else if ((this.bn.a.length < 30)) {
    var a = new $ac_O(1);
    a.a[0] = elem;
    var data2$6 = $m_sci_VectorStatics$().B(this.e, this.bn);
    var length0$6 = ((1 + this.j) | 0);
    return new $c_sci_Vector2(a, 1, data2$6, this.i, length0$6);
  } else {
    var a$1 = new $ac_O(1);
    a$1.a[0] = elem;
    var x = this.e;
    var a$2 = new ($d_O.r().r().C)(1);
    a$2.a[0] = x;
    return new $c_sci_Vector3(a$1, 1, a$2, ((1 + this.bP) | 0), $m_sci_VectorStatics$().by, this.bn, this.i, ((1 + this.j) | 0));
  }
});
$p.dq = (function(f) {
  var prefix1$7 = $m_sci_VectorStatics$().d5(this.e, f);
  var data2$7 = $m_sci_VectorStatics$().aJ(2, this.bn, f);
  var suffix1$7 = $m_sci_VectorStatics$().d5(this.i, f);
  return new $c_sci_Vector2(prefix1$7, this.bP, data2$7, suffix1$7, this.j);
});
$p.dY = (function() {
  return 3;
});
$p.dX = (function(idx) {
  switch (idx) {
    case 0: {
      return this.e;
      break;
    }
    case 1: {
      return this.bn;
      break;
    }
    case 2: {
      return this.i;
      break;
    }
    default: {
      throw new $c_s_MatchError(idx);
    }
  }
});
$p.d = (function(v1) {
  var index = (v1 | 0);
  if (((index >= 0) && (index < this.j))) {
    var io = ((index - this.bP) | 0);
    if ((io >= 0)) {
      var i2 = ((io >>> 5) | 0);
      var i1 = (31 & io);
      return ((i2 < this.bn.a.length) ? this.bn.a[i2].a[i1] : this.i.a[(31 & io)]);
    } else {
      return this.e.a[index];
    }
  } else {
    throw this.bi(index);
  }
});
$p.bp = (function(elem) {
  return this.cJ(elem);
});
$p.aa = (function(f) {
  return this.dq(f);
});
var $d_sci_Vector2 = new $TypeData().i($c_sci_Vector2, "scala.collection.immutable.Vector2", ({
  gy: 1,
  ap: 1,
  aq: 1,
  ag: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a: 1,
  X: 1
}));
/** @constructor */
function $c_sci_Vector3(_prefix1, len1, prefix2, len12, data3, suffix2, _suffix1, _length0) {
  this.e = null;
  this.i = null;
  this.j = 0;
  this.bw = 0;
  this.bx = null;
  this.bf = 0;
  this.aX = null;
  this.b3 = null;
  this.bw = len1;
  this.bx = prefix2;
  this.bf = len12;
  this.aX = data3;
  this.b3 = suffix2;
  $ct_sci_BigVector__AO__AO__I__(this, _prefix1, _suffix1, _length0);
}
$p = $c_sci_Vector3.prototype = new $h_sci_BigVector();
$p.constructor = $c_sci_Vector3;
/** @constructor */
function $h_sci_Vector3() {
}
$h_sci_Vector3.prototype = $p;
$p.r = (function(index) {
  if (((index >= 0) && (index < this.j))) {
    var io = ((index - this.bf) | 0);
    if ((io >= 0)) {
      var i3 = ((io >>> 10) | 0);
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      return ((i3 < this.aX.a.length) ? this.aX.a[i3].a[i2].a[i1] : ((i2 < this.b3.a.length) ? this.b3.a[i2].a[i1] : this.i.a[i1]));
    } else if ((index >= this.bw)) {
      var io$2 = ((index - this.bw) | 0);
      return this.bx.a[((io$2 >>> 5) | 0)].a[(31 & io$2)];
    } else {
      return this.e.a[index];
    }
  } else {
    throw this.bi(index);
  }
});
$p.er = (function(index, elem) {
  if (((index >= 0) && (index < this.j))) {
    if ((index >= this.bf)) {
      var io = ((index - this.bf) | 0);
      var i3 = ((io >>> 10) | 0);
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      if ((i3 < this.aX.a.length)) {
        var a3 = this.aX;
        var a3c = a3.h();
        var a2 = a3c.a[i3];
        var a2c = a2.h();
        var a1 = a2c.a[i2];
        var a1c = a1.h();
        a1c.a[i1] = elem;
        a2c.a[i2] = a1c;
        a3c.a[i3] = a2c;
        return new $c_sci_Vector3(this.e, this.bw, this.bx, this.bf, a3c, this.b3, this.i, this.j);
      } else if ((i2 < this.b3.a.length)) {
        var a2$1 = this.b3;
        var a2c$1 = a2$1.h();
        var a1$1 = a2c$1.a[i2];
        var a1c$1 = a1$1.h();
        a1c$1.a[i1] = elem;
        a2c$1.a[i2] = a1c$1;
        return new $c_sci_Vector3(this.e, this.bw, this.bx, this.bf, this.aX, a2c$1, this.i, this.j);
      } else {
        var a1$2 = this.i;
        var a1c$2 = a1$2.h();
        a1c$2.a[i1] = elem;
        return new $c_sci_Vector3(this.e, this.bw, this.bx, this.bf, this.aX, this.b3, a1c$2, this.j);
      }
    } else if ((index >= this.bw)) {
      var io$2 = ((index - this.bw) | 0);
      var a2$2 = this.bx;
      var idx2 = ((io$2 >>> 5) | 0);
      var idx1 = (31 & io$2);
      var a2c$2 = a2$2.h();
      var a1$3 = a2c$2.a[idx2];
      var a1c$3 = a1$3.h();
      a1c$3.a[idx1] = elem;
      a2c$2.a[idx2] = a1c$3;
      return new $c_sci_Vector3(this.e, this.bw, a2c$2, this.bf, this.aX, this.b3, this.i, this.j);
    } else {
      var a1$4 = this.e;
      var a1c$4 = a1$4.h();
      a1c$4.a[index] = elem;
      return new $c_sci_Vector3(a1c$4, this.bw, this.bx, this.bf, this.aX, this.b3, this.i, this.j);
    }
  } else {
    throw this.bi(index);
  }
});
$p.dM = (function(elem) {
  if ((this.i.a.length < 32)) {
    var suffix1$16 = $m_sci_VectorStatics$().go(this.i, elem);
    var length0$16 = ((1 + this.j) | 0);
    return new $c_sci_Vector3(this.e, this.bw, this.bx, this.bf, this.aX, this.b3, suffix1$16, length0$16);
  } else if ((this.b3.a.length < 31)) {
    var suffix2$6 = $m_sci_VectorStatics$().F(this.b3, this.i);
    var a = new $ac_O(1);
    a.a[0] = elem;
    var length0$17 = ((1 + this.j) | 0);
    return new $c_sci_Vector3(this.e, this.bw, this.bx, this.bf, this.aX, suffix2$6, a, length0$17);
  } else if ((this.aX.a.length < 30)) {
    var data3$7 = $m_sci_VectorStatics$().F(this.aX, $m_sci_VectorStatics$().F(this.b3, this.i));
    var a$1 = new $ac_O(1);
    a$1.a[0] = elem;
    var length0$18 = ((1 + this.j) | 0);
    return new $c_sci_Vector3(this.e, this.bw, this.bx, this.bf, data3$7, $m_sci_VectorStatics$().ao, a$1, length0$18);
  } else {
    var $x_8 = this.e;
    var $x_7 = this.bw;
    var $x_6 = this.bx;
    var $x_5 = this.bf;
    var $x_4 = this.aX;
    var $x_3 = this.bf;
    var $x_2 = $m_sci_VectorStatics$().dh;
    var x = $m_sci_VectorStatics$().F(this.b3, this.i);
    var a$2 = new ($d_O.r().r().r().C)(1);
    a$2.a[0] = x;
    var $x_1 = $m_sci_VectorStatics$().ao;
    var a$3 = new $ac_O(1);
    a$3.a[0] = elem;
    return new $c_sci_Vector4($x_8, $x_7, $x_6, $x_5, $x_4, ((30720 + $x_3) | 0), $x_2, a$2, $x_1, a$3, ((1 + this.j) | 0));
  }
});
$p.cJ = (function(elem) {
  if ((this.bw < 32)) {
    var prefix1$18 = $m_sci_VectorStatics$().gp(elem, this.e);
    var len1$18 = ((1 + this.bw) | 0);
    var len12$8 = ((1 + this.bf) | 0);
    var length0$19 = ((1 + this.j) | 0);
    return new $c_sci_Vector3(prefix1$18, len1$18, this.bx, len12$8, this.aX, this.b3, this.i, length0$19);
  } else if ((this.bf < 1024)) {
    var a = new $ac_O(1);
    a.a[0] = elem;
    var prefix2$9 = $m_sci_VectorStatics$().B(this.e, this.bx);
    var len12$9 = ((1 + this.bf) | 0);
    var length0$20 = ((1 + this.j) | 0);
    return new $c_sci_Vector3(a, 1, prefix2$9, len12$9, this.aX, this.b3, this.i, length0$20);
  } else if ((this.aX.a.length < 30)) {
    var a$1 = new $ac_O(1);
    a$1.a[0] = elem;
    var data3$10 = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.bx), this.aX);
    var length0$21 = ((1 + this.j) | 0);
    var suffix2$9 = this.b3;
    var suffix1$21 = this.i;
    return new $c_sci_Vector3(a$1, 1, $m_sci_VectorStatics$().ao, 1, data3$10, suffix2$9, suffix1$21, length0$21);
  } else {
    var a$2 = new $ac_O(1);
    a$2.a[0] = elem;
    var $x_1 = $m_sci_VectorStatics$().ao;
    var x = $m_sci_VectorStatics$().B(this.e, this.bx);
    var a$3 = new ($d_O.r().r().r().C)(1);
    a$3.a[0] = x;
    return new $c_sci_Vector4(a$2, 1, $x_1, 1, a$3, ((1 + this.bf) | 0), $m_sci_VectorStatics$().dh, this.aX, this.b3, this.i, ((1 + this.j) | 0));
  }
});
$p.dq = (function(f) {
  var prefix1$21 = $m_sci_VectorStatics$().d5(this.e, f);
  var prefix2$10 = $m_sci_VectorStatics$().aJ(2, this.bx, f);
  var data3$11 = $m_sci_VectorStatics$().aJ(3, this.aX, f);
  var suffix2$10 = $m_sci_VectorStatics$().aJ(2, this.b3, f);
  var suffix1$22 = $m_sci_VectorStatics$().d5(this.i, f);
  return new $c_sci_Vector3(prefix1$21, this.bw, prefix2$10, this.bf, data3$11, suffix2$10, suffix1$22, this.j);
});
$p.dY = (function() {
  return 5;
});
$p.dX = (function(idx) {
  switch (idx) {
    case 0: {
      return this.e;
      break;
    }
    case 1: {
      return this.bx;
      break;
    }
    case 2: {
      return this.aX;
      break;
    }
    case 3: {
      return this.b3;
      break;
    }
    case 4: {
      return this.i;
      break;
    }
    default: {
      throw new $c_s_MatchError(idx);
    }
  }
});
$p.d = (function(v1) {
  var index = (v1 | 0);
  if (((index >= 0) && (index < this.j))) {
    var io = ((index - this.bf) | 0);
    if ((io >= 0)) {
      var i3 = ((io >>> 10) | 0);
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      return ((i3 < this.aX.a.length) ? this.aX.a[i3].a[i2].a[i1] : ((i2 < this.b3.a.length) ? this.b3.a[i2].a[i1] : this.i.a[i1]));
    } else if ((index >= this.bw)) {
      var io$2 = ((index - this.bw) | 0);
      return this.bx.a[((io$2 >>> 5) | 0)].a[(31 & io$2)];
    } else {
      return this.e.a[index];
    }
  } else {
    throw this.bi(index);
  }
});
$p.bp = (function(elem) {
  return this.cJ(elem);
});
$p.aa = (function(f) {
  return this.dq(f);
});
var $d_sci_Vector3 = new $TypeData().i($c_sci_Vector3, "scala.collection.immutable.Vector3", ({
  gz: 1,
  ap: 1,
  aq: 1,
  ag: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a: 1,
  X: 1
}));
/** @constructor */
function $c_sci_Vector4(_prefix1, len1, prefix2, len12, prefix3, len123, data4, suffix3, suffix2, _suffix1, _length0) {
  this.e = null;
  this.i = null;
  this.j = 0;
  this.ba = 0;
  this.b5 = null;
  this.b4 = 0;
  this.b6 = null;
  this.aQ = 0;
  this.as = null;
  this.aB = null;
  this.aA = null;
  this.ba = len1;
  this.b5 = prefix2;
  this.b4 = len12;
  this.b6 = prefix3;
  this.aQ = len123;
  this.as = data4;
  this.aB = suffix3;
  this.aA = suffix2;
  $ct_sci_BigVector__AO__AO__I__(this, _prefix1, _suffix1, _length0);
}
$p = $c_sci_Vector4.prototype = new $h_sci_BigVector();
$p.constructor = $c_sci_Vector4;
/** @constructor */
function $h_sci_Vector4() {
}
$h_sci_Vector4.prototype = $p;
$p.r = (function(index) {
  if (((index >= 0) && (index < this.j))) {
    var io = ((index - this.aQ) | 0);
    if ((io >= 0)) {
      var i4 = ((io >>> 15) | 0);
      var i3 = (31 & ((io >>> 10) | 0));
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      return ((i4 < this.as.a.length) ? this.as.a[i4].a[i3].a[i2].a[i1] : ((i3 < this.aB.a.length) ? this.aB.a[i3].a[i2].a[i1] : ((i2 < this.aA.a.length) ? this.aA.a[i2].a[i1] : this.i.a[i1])));
    } else if ((index >= this.b4)) {
      var io$2 = ((index - this.b4) | 0);
      return this.b6.a[((io$2 >>> 10) | 0)].a[(31 & ((io$2 >>> 5) | 0))].a[(31 & io$2)];
    } else if ((index >= this.ba)) {
      var io$3 = ((index - this.ba) | 0);
      return this.b5.a[((io$3 >>> 5) | 0)].a[(31 & io$3)];
    } else {
      return this.e.a[index];
    }
  } else {
    throw this.bi(index);
  }
});
$p.er = (function(index, elem) {
  if (((index >= 0) && (index < this.j))) {
    if ((index >= this.aQ)) {
      var io = ((index - this.aQ) | 0);
      var i4 = ((io >>> 15) | 0);
      var i3 = (31 & ((io >>> 10) | 0));
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      if ((i4 < this.as.a.length)) {
        var a4 = this.as;
        var a4c = a4.h();
        var a3 = a4c.a[i4];
        var a3c = a3.h();
        var a2 = a3c.a[i3];
        var a2c = a2.h();
        var a1 = a2c.a[i2];
        var a1c = a1.h();
        a1c.a[i1] = elem;
        a2c.a[i2] = a1c;
        a3c.a[i3] = a2c;
        a4c.a[i4] = a3c;
        return new $c_sci_Vector4(this.e, this.ba, this.b5, this.b4, this.b6, this.aQ, a4c, this.aB, this.aA, this.i, this.j);
      } else if ((i3 < this.aB.a.length)) {
        var a3$1 = this.aB;
        var a3c$1 = a3$1.h();
        var a2$1 = a3c$1.a[i3];
        var a2c$1 = a2$1.h();
        var a1$1 = a2c$1.a[i2];
        var a1c$1 = a1$1.h();
        a1c$1.a[i1] = elem;
        a2c$1.a[i2] = a1c$1;
        a3c$1.a[i3] = a2c$1;
        return new $c_sci_Vector4(this.e, this.ba, this.b5, this.b4, this.b6, this.aQ, this.as, a3c$1, this.aA, this.i, this.j);
      } else if ((i2 < this.aA.a.length)) {
        var a2$2 = this.aA;
        var a2c$2 = a2$2.h();
        var a1$2 = a2c$2.a[i2];
        var a1c$2 = a1$2.h();
        a1c$2.a[i1] = elem;
        a2c$2.a[i2] = a1c$2;
        return new $c_sci_Vector4(this.e, this.ba, this.b5, this.b4, this.b6, this.aQ, this.as, this.aB, a2c$2, this.i, this.j);
      } else {
        var a1$3 = this.i;
        var a1c$3 = a1$3.h();
        a1c$3.a[i1] = elem;
        return new $c_sci_Vector4(this.e, this.ba, this.b5, this.b4, this.b6, this.aQ, this.as, this.aB, this.aA, a1c$3, this.j);
      }
    } else if ((index >= this.b4)) {
      var io$2 = ((index - this.b4) | 0);
      var a3$2 = this.b6;
      var idx3 = ((io$2 >>> 10) | 0);
      var idx2 = (31 & ((io$2 >>> 5) | 0));
      var idx1 = (31 & io$2);
      var a3c$2 = a3$2.h();
      var a2$3 = a3c$2.a[idx3];
      var a2c$3 = a2$3.h();
      var a1$4 = a2c$3.a[idx2];
      var a1c$4 = a1$4.h();
      a1c$4.a[idx1] = elem;
      a2c$3.a[idx2] = a1c$4;
      a3c$2.a[idx3] = a2c$3;
      return new $c_sci_Vector4(this.e, this.ba, this.b5, this.b4, a3c$2, this.aQ, this.as, this.aB, this.aA, this.i, this.j);
    } else if ((index >= this.ba)) {
      var io$3 = ((index - this.ba) | 0);
      var a2$4 = this.b5;
      var idx2$1 = ((io$3 >>> 5) | 0);
      var idx1$1 = (31 & io$3);
      var a2c$4 = a2$4.h();
      var a1$5 = a2c$4.a[idx2$1];
      var a1c$5 = a1$5.h();
      a1c$5.a[idx1$1] = elem;
      a2c$4.a[idx2$1] = a1c$5;
      return new $c_sci_Vector4(this.e, this.ba, a2c$4, this.b4, this.b6, this.aQ, this.as, this.aB, this.aA, this.i, this.j);
    } else {
      var a1$6 = this.e;
      var a1c$6 = a1$6.h();
      a1c$6.a[index] = elem;
      return new $c_sci_Vector4(a1c$6, this.ba, this.b5, this.b4, this.b6, this.aQ, this.as, this.aB, this.aA, this.i, this.j);
    }
  } else {
    throw this.bi(index);
  }
});
$p.dM = (function(elem) {
  if ((this.i.a.length < 32)) {
    var suffix1$33 = $m_sci_VectorStatics$().go(this.i, elem);
    var length0$33 = ((1 + this.j) | 0);
    return new $c_sci_Vector4(this.e, this.ba, this.b5, this.b4, this.b6, this.aQ, this.as, this.aB, this.aA, suffix1$33, length0$33);
  } else if ((this.aA.a.length < 31)) {
    var suffix2$22 = $m_sci_VectorStatics$().F(this.aA, this.i);
    var a = new $ac_O(1);
    a.a[0] = elem;
    var length0$34 = ((1 + this.j) | 0);
    return new $c_sci_Vector4(this.e, this.ba, this.b5, this.b4, this.b6, this.aQ, this.as, this.aB, suffix2$22, a, length0$34);
  } else if ((this.aB.a.length < 31)) {
    var suffix3$9 = $m_sci_VectorStatics$().F(this.aB, $m_sci_VectorStatics$().F(this.aA, this.i));
    var a$1 = new $ac_O(1);
    a$1.a[0] = elem;
    var length0$35 = ((1 + this.j) | 0);
    return new $c_sci_Vector4(this.e, this.ba, this.b5, this.b4, this.b6, this.aQ, this.as, suffix3$9, $m_sci_VectorStatics$().ao, a$1, length0$35);
  } else if ((this.as.a.length < 30)) {
    var data4$10 = $m_sci_VectorStatics$().F(this.as, $m_sci_VectorStatics$().F(this.aB, $m_sci_VectorStatics$().F(this.aA, this.i)));
    var a$2 = new $ac_O(1);
    a$2.a[0] = elem;
    var length0$36 = ((1 + this.j) | 0);
    return new $c_sci_Vector4(this.e, this.ba, this.b5, this.b4, this.b6, this.aQ, data4$10, $m_sci_VectorStatics$().by, $m_sci_VectorStatics$().ao, a$2, length0$36);
  } else {
    var $x_11 = this.e;
    var $x_10 = this.ba;
    var $x_9 = this.b5;
    var $x_8 = this.b4;
    var $x_7 = this.b6;
    var $x_6 = this.aQ;
    var $x_5 = this.as;
    var $x_4 = this.aQ;
    var $x_3 = $m_sci_VectorStatics$().h9;
    var x = $m_sci_VectorStatics$().F(this.aB, $m_sci_VectorStatics$().F(this.aA, this.i));
    var a$3 = new ($d_O.r().r().r().r().C)(1);
    a$3.a[0] = x;
    var $x_2 = $m_sci_VectorStatics$().by;
    var $x_1 = $m_sci_VectorStatics$().ao;
    var a$4 = new $ac_O(1);
    a$4.a[0] = elem;
    return new $c_sci_Vector5($x_11, $x_10, $x_9, $x_8, $x_7, $x_6, $x_5, ((983040 + $x_4) | 0), $x_3, a$3, $x_2, $x_1, a$4, ((1 + this.j) | 0));
  }
});
$p.cJ = (function(elem) {
  if ((this.ba < 32)) {
    var prefix1$35 = $m_sci_VectorStatics$().gp(elem, this.e);
    var len1$34 = ((1 + this.ba) | 0);
    var len12$25 = ((1 + this.b4) | 0);
    var len123$11 = ((1 + this.aQ) | 0);
    var length0$37 = ((1 + this.j) | 0);
    return new $c_sci_Vector4(prefix1$35, len1$34, this.b5, len12$25, this.b6, len123$11, this.as, this.aB, this.aA, this.i, length0$37);
  } else if ((this.b4 < 1024)) {
    var a = new $ac_O(1);
    a.a[0] = elem;
    var prefix2$26 = $m_sci_VectorStatics$().B(this.e, this.b5);
    var len12$26 = ((1 + this.b4) | 0);
    var len123$12 = ((1 + this.aQ) | 0);
    var length0$38 = ((1 + this.j) | 0);
    return new $c_sci_Vector4(a, 1, prefix2$26, len12$26, this.b6, len123$12, this.as, this.aB, this.aA, this.i, length0$38);
  } else if ((this.aQ < 32768)) {
    var a$1 = new $ac_O(1);
    a$1.a[0] = elem;
    var prefix3$13 = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.b5), this.b6);
    var len123$13 = ((1 + this.aQ) | 0);
    var length0$39 = ((1 + this.j) | 0);
    var data4$13 = this.as;
    var suffix3$12 = this.aB;
    var suffix2$25 = this.aA;
    var suffix1$39 = this.i;
    return new $c_sci_Vector4(a$1, 1, $m_sci_VectorStatics$().ao, 1, prefix3$13, len123$13, data4$13, suffix3$12, suffix2$25, suffix1$39, length0$39);
  } else if ((this.as.a.length < 30)) {
    var a$2 = new $ac_O(1);
    a$2.a[0] = elem;
    var data4$14 = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.b5), this.b6), this.as);
    var length0$40 = ((1 + this.j) | 0);
    var suffix3$13 = this.aB;
    var suffix2$26 = this.aA;
    var suffix1$40 = this.i;
    return new $c_sci_Vector4(a$2, 1, $m_sci_VectorStatics$().ao, 1, $m_sci_VectorStatics$().by, 1, data4$14, suffix3$13, suffix2$26, suffix1$40, length0$40);
  } else {
    var a$3 = new $ac_O(1);
    a$3.a[0] = elem;
    var $x_2 = $m_sci_VectorStatics$().ao;
    var $x_1 = $m_sci_VectorStatics$().by;
    var x = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.b5), this.b6);
    var a$4 = new ($d_O.r().r().r().r().C)(1);
    a$4.a[0] = x;
    return new $c_sci_Vector5(a$3, 1, $x_2, 1, $x_1, 1, a$4, ((1 + this.aQ) | 0), $m_sci_VectorStatics$().h9, this.as, this.aB, this.aA, this.i, ((1 + this.j) | 0));
  }
});
$p.dq = (function(f) {
  var prefix1$39 = $m_sci_VectorStatics$().d5(this.e, f);
  var prefix2$27 = $m_sci_VectorStatics$().aJ(2, this.b5, f);
  var prefix3$14 = $m_sci_VectorStatics$().aJ(3, this.b6, f);
  var data4$15 = $m_sci_VectorStatics$().aJ(4, this.as, f);
  var suffix3$14 = $m_sci_VectorStatics$().aJ(3, this.aB, f);
  var suffix2$27 = $m_sci_VectorStatics$().aJ(2, this.aA, f);
  var suffix1$41 = $m_sci_VectorStatics$().d5(this.i, f);
  return new $c_sci_Vector4(prefix1$39, this.ba, prefix2$27, this.b4, prefix3$14, this.aQ, data4$15, suffix3$14, suffix2$27, suffix1$41, this.j);
});
$p.dY = (function() {
  return 7;
});
$p.dX = (function(idx) {
  switch (idx) {
    case 0: {
      return this.e;
      break;
    }
    case 1: {
      return this.b5;
      break;
    }
    case 2: {
      return this.b6;
      break;
    }
    case 3: {
      return this.as;
      break;
    }
    case 4: {
      return this.aB;
      break;
    }
    case 5: {
      return this.aA;
      break;
    }
    case 6: {
      return this.i;
      break;
    }
    default: {
      throw new $c_s_MatchError(idx);
    }
  }
});
$p.d = (function(v1) {
  var index = (v1 | 0);
  if (((index >= 0) && (index < this.j))) {
    var io = ((index - this.aQ) | 0);
    if ((io >= 0)) {
      var i4 = ((io >>> 15) | 0);
      var i3 = (31 & ((io >>> 10) | 0));
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      return ((i4 < this.as.a.length) ? this.as.a[i4].a[i3].a[i2].a[i1] : ((i3 < this.aB.a.length) ? this.aB.a[i3].a[i2].a[i1] : ((i2 < this.aA.a.length) ? this.aA.a[i2].a[i1] : this.i.a[i1])));
    } else if ((index >= this.b4)) {
      var io$2 = ((index - this.b4) | 0);
      return this.b6.a[((io$2 >>> 10) | 0)].a[(31 & ((io$2 >>> 5) | 0))].a[(31 & io$2)];
    } else if ((index >= this.ba)) {
      var io$3 = ((index - this.ba) | 0);
      return this.b5.a[((io$3 >>> 5) | 0)].a[(31 & io$3)];
    } else {
      return this.e.a[index];
    }
  } else {
    throw this.bi(index);
  }
});
$p.bp = (function(elem) {
  return this.cJ(elem);
});
$p.aa = (function(f) {
  return this.dq(f);
});
var $d_sci_Vector4 = new $TypeData().i($c_sci_Vector4, "scala.collection.immutable.Vector4", ({
  gA: 1,
  ap: 1,
  aq: 1,
  ag: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a: 1,
  X: 1
}));
/** @constructor */
function $c_sci_Vector5(_prefix1, len1, prefix2, len12, prefix3, len123, prefix4, len1234, data5, suffix4, suffix3, suffix2, _suffix1, _length0) {
  this.e = null;
  this.i = null;
  this.j = 0;
  this.aR = 0;
  this.aD = null;
  this.aL = 0;
  this.aE = null;
  this.aC = 0;
  this.aF = null;
  this.ai = 0;
  this.a0 = null;
  this.a8 = null;
  this.a7 = null;
  this.a6 = null;
  this.aR = len1;
  this.aD = prefix2;
  this.aL = len12;
  this.aE = prefix3;
  this.aC = len123;
  this.aF = prefix4;
  this.ai = len1234;
  this.a0 = data5;
  this.a8 = suffix4;
  this.a7 = suffix3;
  this.a6 = suffix2;
  $ct_sci_BigVector__AO__AO__I__(this, _prefix1, _suffix1, _length0);
}
$p = $c_sci_Vector5.prototype = new $h_sci_BigVector();
$p.constructor = $c_sci_Vector5;
/** @constructor */
function $h_sci_Vector5() {
}
$h_sci_Vector5.prototype = $p;
$p.r = (function(index) {
  if (((index >= 0) && (index < this.j))) {
    var io = ((index - this.ai) | 0);
    if ((io >= 0)) {
      var i5 = ((io >>> 20) | 0);
      var i4 = (31 & ((io >>> 15) | 0));
      var i3 = (31 & ((io >>> 10) | 0));
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      return ((i5 < this.a0.a.length) ? this.a0.a[i5].a[i4].a[i3].a[i2].a[i1] : ((i4 < this.a8.a.length) ? this.a8.a[i4].a[i3].a[i2].a[i1] : ((i3 < this.a7.a.length) ? this.a7.a[i3].a[i2].a[i1] : ((i2 < this.a6.a.length) ? this.a6.a[i2].a[i1] : this.i.a[i1]))));
    } else if ((index >= this.aC)) {
      var io$2 = ((index - this.aC) | 0);
      return this.aF.a[((io$2 >>> 15) | 0)].a[(31 & ((io$2 >>> 10) | 0))].a[(31 & ((io$2 >>> 5) | 0))].a[(31 & io$2)];
    } else if ((index >= this.aL)) {
      var io$3 = ((index - this.aL) | 0);
      return this.aE.a[((io$3 >>> 10) | 0)].a[(31 & ((io$3 >>> 5) | 0))].a[(31 & io$3)];
    } else if ((index >= this.aR)) {
      var io$4 = ((index - this.aR) | 0);
      return this.aD.a[((io$4 >>> 5) | 0)].a[(31 & io$4)];
    } else {
      return this.e.a[index];
    }
  } else {
    throw this.bi(index);
  }
});
$p.er = (function(index, elem) {
  if (((index >= 0) && (index < this.j))) {
    if ((index >= this.ai)) {
      var io = ((index - this.ai) | 0);
      var i5 = ((io >>> 20) | 0);
      var i4 = (31 & ((io >>> 15) | 0));
      var i3 = (31 & ((io >>> 10) | 0));
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      if ((i5 < this.a0.a.length)) {
        var a5 = this.a0;
        var a5c = a5.h();
        var a4 = a5c.a[i5];
        var a4c = a4.h();
        var a3 = a4c.a[i4];
        var a3c = a3.h();
        var a2 = a3c.a[i3];
        var a2c = a2.h();
        var a1 = a2c.a[i2];
        var a1c = a1.h();
        a1c.a[i1] = elem;
        a2c.a[i2] = a1c;
        a3c.a[i3] = a2c;
        a4c.a[i4] = a3c;
        a5c.a[i5] = a4c;
        return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, this.aE, this.aC, this.aF, this.ai, a5c, this.a8, this.a7, this.a6, this.i, this.j);
      } else if ((i4 < this.a8.a.length)) {
        var a4$1 = this.a8;
        var a4c$1 = a4$1.h();
        var a3$1 = a4c$1.a[i4];
        var a3c$1 = a3$1.h();
        var a2$1 = a3c$1.a[i3];
        var a2c$1 = a2$1.h();
        var a1$1 = a2c$1.a[i2];
        var a1c$1 = a1$1.h();
        a1c$1.a[i1] = elem;
        a2c$1.a[i2] = a1c$1;
        a3c$1.a[i3] = a2c$1;
        a4c$1.a[i4] = a3c$1;
        return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, this.aE, this.aC, this.aF, this.ai, this.a0, a4c$1, this.a7, this.a6, this.i, this.j);
      } else if ((i3 < this.a7.a.length)) {
        var a3$2 = this.a7;
        var a3c$2 = a3$2.h();
        var a2$2 = a3c$2.a[i3];
        var a2c$2 = a2$2.h();
        var a1$2 = a2c$2.a[i2];
        var a1c$2 = a1$2.h();
        a1c$2.a[i1] = elem;
        a2c$2.a[i2] = a1c$2;
        a3c$2.a[i3] = a2c$2;
        return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, this.aE, this.aC, this.aF, this.ai, this.a0, this.a8, a3c$2, this.a6, this.i, this.j);
      } else if ((i2 < this.a6.a.length)) {
        var a2$3 = this.a6;
        var a2c$3 = a2$3.h();
        var a1$3 = a2c$3.a[i2];
        var a1c$3 = a1$3.h();
        a1c$3.a[i1] = elem;
        a2c$3.a[i2] = a1c$3;
        return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, this.aE, this.aC, this.aF, this.ai, this.a0, this.a8, this.a7, a2c$3, this.i, this.j);
      } else {
        var a1$4 = this.i;
        var a1c$4 = a1$4.h();
        a1c$4.a[i1] = elem;
        return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, this.aE, this.aC, this.aF, this.ai, this.a0, this.a8, this.a7, this.a6, a1c$4, this.j);
      }
    } else if ((index >= this.aC)) {
      var io$2 = ((index - this.aC) | 0);
      var a4$2 = this.aF;
      var idx4 = ((io$2 >>> 15) | 0);
      var idx3 = (31 & ((io$2 >>> 10) | 0));
      var idx2 = (31 & ((io$2 >>> 5) | 0));
      var idx1 = (31 & io$2);
      var a4c$2 = a4$2.h();
      var a3$3 = a4c$2.a[idx4];
      var a3c$3 = a3$3.h();
      var a2$4 = a3c$3.a[idx3];
      var a2c$4 = a2$4.h();
      var a1$5 = a2c$4.a[idx2];
      var a1c$5 = a1$5.h();
      a1c$5.a[idx1] = elem;
      a2c$4.a[idx2] = a1c$5;
      a3c$3.a[idx3] = a2c$4;
      a4c$2.a[idx4] = a3c$3;
      return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, this.aE, this.aC, a4c$2, this.ai, this.a0, this.a8, this.a7, this.a6, this.i, this.j);
    } else if ((index >= this.aL)) {
      var io$3 = ((index - this.aL) | 0);
      var a3$4 = this.aE;
      var idx3$1 = ((io$3 >>> 10) | 0);
      var idx2$1 = (31 & ((io$3 >>> 5) | 0));
      var idx1$1 = (31 & io$3);
      var a3c$4 = a3$4.h();
      var a2$5 = a3c$4.a[idx3$1];
      var a2c$5 = a2$5.h();
      var a1$6 = a2c$5.a[idx2$1];
      var a1c$6 = a1$6.h();
      a1c$6.a[idx1$1] = elem;
      a2c$5.a[idx2$1] = a1c$6;
      a3c$4.a[idx3$1] = a2c$5;
      return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, a3c$4, this.aC, this.aF, this.ai, this.a0, this.a8, this.a7, this.a6, this.i, this.j);
    } else if ((index >= this.aR)) {
      var io$4 = ((index - this.aR) | 0);
      var a2$6 = this.aD;
      var idx2$2 = ((io$4 >>> 5) | 0);
      var idx1$2 = (31 & io$4);
      var a2c$6 = a2$6.h();
      var a1$7 = a2c$6.a[idx2$2];
      var a1c$7 = a1$7.h();
      a1c$7.a[idx1$2] = elem;
      a2c$6.a[idx2$2] = a1c$7;
      return new $c_sci_Vector5(this.e, this.aR, a2c$6, this.aL, this.aE, this.aC, this.aF, this.ai, this.a0, this.a8, this.a7, this.a6, this.i, this.j);
    } else {
      var a1$8 = this.e;
      var a1c$8 = a1$8.h();
      a1c$8.a[index] = elem;
      return new $c_sci_Vector5(a1c$8, this.aR, this.aD, this.aL, this.aE, this.aC, this.aF, this.ai, this.a0, this.a8, this.a7, this.a6, this.i, this.j);
    }
  } else {
    throw this.bi(index);
  }
});
$p.dM = (function(elem) {
  if ((this.i.a.length < 32)) {
    var suffix1$54 = $m_sci_VectorStatics$().go(this.i, elem);
    var length0$54 = ((1 + this.j) | 0);
    return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, this.aE, this.aC, this.aF, this.ai, this.a0, this.a8, this.a7, this.a6, suffix1$54, length0$54);
  } else if ((this.a6.a.length < 31)) {
    var suffix2$41 = $m_sci_VectorStatics$().F(this.a6, this.i);
    var a = new $ac_O(1);
    a.a[0] = elem;
    var length0$55 = ((1 + this.j) | 0);
    return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, this.aE, this.aC, this.aF, this.ai, this.a0, this.a8, this.a7, suffix2$41, a, length0$55);
  } else if ((this.a7.a.length < 31)) {
    var suffix3$29 = $m_sci_VectorStatics$().F(this.a7, $m_sci_VectorStatics$().F(this.a6, this.i));
    var a$1 = new $ac_O(1);
    a$1.a[0] = elem;
    var length0$56 = ((1 + this.j) | 0);
    return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, this.aE, this.aC, this.aF, this.ai, this.a0, this.a8, suffix3$29, $m_sci_VectorStatics$().ao, a$1, length0$56);
  } else if ((this.a8.a.length < 31)) {
    var suffix4$12 = $m_sci_VectorStatics$().F(this.a8, $m_sci_VectorStatics$().F(this.a7, $m_sci_VectorStatics$().F(this.a6, this.i)));
    var a$2 = new $ac_O(1);
    a$2.a[0] = elem;
    var length0$57 = ((1 + this.j) | 0);
    return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, this.aE, this.aC, this.aF, this.ai, this.a0, suffix4$12, $m_sci_VectorStatics$().by, $m_sci_VectorStatics$().ao, a$2, length0$57);
  } else if ((this.a0.a.length < 30)) {
    var data5$13 = $m_sci_VectorStatics$().F(this.a0, $m_sci_VectorStatics$().F(this.a8, $m_sci_VectorStatics$().F(this.a7, $m_sci_VectorStatics$().F(this.a6, this.i))));
    var a$3 = new $ac_O(1);
    a$3.a[0] = elem;
    var length0$58 = ((1 + this.j) | 0);
    return new $c_sci_Vector5(this.e, this.aR, this.aD, this.aL, this.aE, this.aC, this.aF, this.ai, data5$13, $m_sci_VectorStatics$().dh, $m_sci_VectorStatics$().by, $m_sci_VectorStatics$().ao, a$3, length0$58);
  } else {
    var $x_14 = this.e;
    var $x_13 = this.aR;
    var $x_12 = this.aD;
    var $x_11 = this.aL;
    var $x_10 = this.aE;
    var $x_9 = this.aC;
    var $x_8 = this.aF;
    var $x_7 = this.ai;
    var $x_6 = this.a0;
    var $x_5 = this.ai;
    var $x_4 = $m_sci_VectorStatics$().jW;
    var x = $m_sci_VectorStatics$().F(this.a8, $m_sci_VectorStatics$().F(this.a7, $m_sci_VectorStatics$().F(this.a6, this.i)));
    var a$4 = new ($d_O.r().r().r().r().r().C)(1);
    a$4.a[0] = x;
    var $x_3 = $m_sci_VectorStatics$().dh;
    var $x_2 = $m_sci_VectorStatics$().by;
    var $x_1 = $m_sci_VectorStatics$().ao;
    var a$5 = new $ac_O(1);
    a$5.a[0] = elem;
    return new $c_sci_Vector6($x_14, $x_13, $x_12, $x_11, $x_10, $x_9, $x_8, $x_7, $x_6, ((31457280 + $x_5) | 0), $x_4, a$4, $x_3, $x_2, $x_1, a$5, ((1 + this.j) | 0));
  }
});
$p.cJ = (function(elem) {
  if ((this.aR < 32)) {
    var prefix1$56 = $m_sci_VectorStatics$().gp(elem, this.e);
    var len1$53 = ((1 + this.aR) | 0);
    var len12$45 = ((1 + this.aL) | 0);
    var len123$32 = ((1 + this.aC) | 0);
    var len1234$14 = ((1 + this.ai) | 0);
    var length0$59 = ((1 + this.j) | 0);
    return new $c_sci_Vector5(prefix1$56, len1$53, this.aD, len12$45, this.aE, len123$32, this.aF, len1234$14, this.a0, this.a8, this.a7, this.a6, this.i, length0$59);
  } else if ((this.aL < 1024)) {
    var a = new $ac_O(1);
    a.a[0] = elem;
    var prefix2$46 = $m_sci_VectorStatics$().B(this.e, this.aD);
    var len12$46 = ((1 + this.aL) | 0);
    var len123$33 = ((1 + this.aC) | 0);
    var len1234$15 = ((1 + this.ai) | 0);
    var length0$60 = ((1 + this.j) | 0);
    return new $c_sci_Vector5(a, 1, prefix2$46, len12$46, this.aE, len123$33, this.aF, len1234$15, this.a0, this.a8, this.a7, this.a6, this.i, length0$60);
  } else if ((this.aC < 32768)) {
    var a$1 = new $ac_O(1);
    a$1.a[0] = elem;
    var prefix3$34 = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.aD), this.aE);
    var len123$34 = ((1 + this.aC) | 0);
    var len1234$16 = ((1 + this.ai) | 0);
    var length0$61 = ((1 + this.j) | 0);
    var prefix4$16 = this.aF;
    var data5$16 = this.a0;
    var suffix4$15 = this.a8;
    var suffix3$32 = this.a7;
    var suffix2$44 = this.a6;
    var suffix1$61 = this.i;
    return new $c_sci_Vector5(a$1, 1, $m_sci_VectorStatics$().ao, 1, prefix3$34, len123$34, prefix4$16, len1234$16, data5$16, suffix4$15, suffix3$32, suffix2$44, suffix1$61, length0$61);
  } else if ((this.ai < 1048576)) {
    var a$2 = new $ac_O(1);
    a$2.a[0] = elem;
    var prefix4$17 = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.aD), this.aE), this.aF);
    var len1234$17 = ((1 + this.ai) | 0);
    var length0$62 = ((1 + this.j) | 0);
    var data5$17 = this.a0;
    var suffix4$16 = this.a8;
    var suffix3$33 = this.a7;
    var suffix2$45 = this.a6;
    var suffix1$62 = this.i;
    return new $c_sci_Vector5(a$2, 1, $m_sci_VectorStatics$().ao, 1, $m_sci_VectorStatics$().by, 1, prefix4$17, len1234$17, data5$17, suffix4$16, suffix3$33, suffix2$45, suffix1$62, length0$62);
  } else if ((this.a0.a.length < 30)) {
    var a$3 = new $ac_O(1);
    a$3.a[0] = elem;
    var data5$18 = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.aD), this.aE), this.aF), this.a0);
    var length0$63 = ((1 + this.j) | 0);
    var suffix4$17 = this.a8;
    var suffix3$34 = this.a7;
    var suffix2$46 = this.a6;
    var suffix1$63 = this.i;
    return new $c_sci_Vector5(a$3, 1, $m_sci_VectorStatics$().ao, 1, $m_sci_VectorStatics$().by, 1, $m_sci_VectorStatics$().dh, 1, data5$18, suffix4$17, suffix3$34, suffix2$46, suffix1$63, length0$63);
  } else {
    var a$4 = new $ac_O(1);
    a$4.a[0] = elem;
    var $x_3 = $m_sci_VectorStatics$().ao;
    var $x_2 = $m_sci_VectorStatics$().by;
    var $x_1 = $m_sci_VectorStatics$().dh;
    var x = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.aD), this.aE), this.aF);
    var a$5 = new ($d_O.r().r().r().r().r().C)(1);
    a$5.a[0] = x;
    return new $c_sci_Vector6(a$4, 1, $x_3, 1, $x_2, 1, $x_1, 1, a$5, ((1 + this.ai) | 0), $m_sci_VectorStatics$().jW, this.a0, this.a8, this.a7, this.a6, this.i, ((1 + this.j) | 0));
  }
});
$p.dq = (function(f) {
  var prefix1$61 = $m_sci_VectorStatics$().d5(this.e, f);
  var prefix2$47 = $m_sci_VectorStatics$().aJ(2, this.aD, f);
  var prefix3$35 = $m_sci_VectorStatics$().aJ(3, this.aE, f);
  var prefix4$18 = $m_sci_VectorStatics$().aJ(4, this.aF, f);
  var data5$19 = $m_sci_VectorStatics$().aJ(5, this.a0, f);
  var suffix4$18 = $m_sci_VectorStatics$().aJ(4, this.a8, f);
  var suffix3$35 = $m_sci_VectorStatics$().aJ(3, this.a7, f);
  var suffix2$47 = $m_sci_VectorStatics$().aJ(2, this.a6, f);
  var suffix1$64 = $m_sci_VectorStatics$().d5(this.i, f);
  return new $c_sci_Vector5(prefix1$61, this.aR, prefix2$47, this.aL, prefix3$35, this.aC, prefix4$18, this.ai, data5$19, suffix4$18, suffix3$35, suffix2$47, suffix1$64, this.j);
});
$p.dY = (function() {
  return 9;
});
$p.dX = (function(idx) {
  switch (idx) {
    case 0: {
      return this.e;
      break;
    }
    case 1: {
      return this.aD;
      break;
    }
    case 2: {
      return this.aE;
      break;
    }
    case 3: {
      return this.aF;
      break;
    }
    case 4: {
      return this.a0;
      break;
    }
    case 5: {
      return this.a8;
      break;
    }
    case 6: {
      return this.a7;
      break;
    }
    case 7: {
      return this.a6;
      break;
    }
    case 8: {
      return this.i;
      break;
    }
    default: {
      throw new $c_s_MatchError(idx);
    }
  }
});
$p.d = (function(v1) {
  var index = (v1 | 0);
  if (((index >= 0) && (index < this.j))) {
    var io = ((index - this.ai) | 0);
    if ((io >= 0)) {
      var i5 = ((io >>> 20) | 0);
      var i4 = (31 & ((io >>> 15) | 0));
      var i3 = (31 & ((io >>> 10) | 0));
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      return ((i5 < this.a0.a.length) ? this.a0.a[i5].a[i4].a[i3].a[i2].a[i1] : ((i4 < this.a8.a.length) ? this.a8.a[i4].a[i3].a[i2].a[i1] : ((i3 < this.a7.a.length) ? this.a7.a[i3].a[i2].a[i1] : ((i2 < this.a6.a.length) ? this.a6.a[i2].a[i1] : this.i.a[i1]))));
    } else if ((index >= this.aC)) {
      var io$2 = ((index - this.aC) | 0);
      return this.aF.a[((io$2 >>> 15) | 0)].a[(31 & ((io$2 >>> 10) | 0))].a[(31 & ((io$2 >>> 5) | 0))].a[(31 & io$2)];
    } else if ((index >= this.aL)) {
      var io$3 = ((index - this.aL) | 0);
      return this.aE.a[((io$3 >>> 10) | 0)].a[(31 & ((io$3 >>> 5) | 0))].a[(31 & io$3)];
    } else if ((index >= this.aR)) {
      var io$4 = ((index - this.aR) | 0);
      return this.aD.a[((io$4 >>> 5) | 0)].a[(31 & io$4)];
    } else {
      return this.e.a[index];
    }
  } else {
    throw this.bi(index);
  }
});
$p.bp = (function(elem) {
  return this.cJ(elem);
});
$p.aa = (function(f) {
  return this.dq(f);
});
var $d_sci_Vector5 = new $TypeData().i($c_sci_Vector5, "scala.collection.immutable.Vector5", ({
  gB: 1,
  ap: 1,
  aq: 1,
  ag: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a: 1,
  X: 1
}));
/** @constructor */
function $c_sci_Vector6(_prefix1, len1, prefix2, len12, prefix3, len123, prefix4, len1234, prefix5, len12345, data6, suffix5, suffix4, suffix3, suffix2, _suffix1, _length0) {
  this.e = null;
  this.i = null;
  this.j = 0;
  this.aG = 0;
  this.ak = null;
  this.at = 0;
  this.al = null;
  this.aj = 0;
  this.am = null;
  this.ad = 0;
  this.an = null;
  this.ae = 0;
  this.R = null;
  this.Y = null;
  this.X = null;
  this.W = null;
  this.V = null;
  this.aG = len1;
  this.ak = prefix2;
  this.at = len12;
  this.al = prefix3;
  this.aj = len123;
  this.am = prefix4;
  this.ad = len1234;
  this.an = prefix5;
  this.ae = len12345;
  this.R = data6;
  this.Y = suffix5;
  this.X = suffix4;
  this.W = suffix3;
  this.V = suffix2;
  $ct_sci_BigVector__AO__AO__I__(this, _prefix1, _suffix1, _length0);
}
$p = $c_sci_Vector6.prototype = new $h_sci_BigVector();
$p.constructor = $c_sci_Vector6;
/** @constructor */
function $h_sci_Vector6() {
}
$h_sci_Vector6.prototype = $p;
$p.r = (function(index) {
  if (((index >= 0) && (index < this.j))) {
    var io = ((index - this.ae) | 0);
    if ((io >= 0)) {
      var i6 = ((io >>> 25) | 0);
      var i5 = (31 & ((io >>> 20) | 0));
      var i4 = (31 & ((io >>> 15) | 0));
      var i3 = (31 & ((io >>> 10) | 0));
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      return ((i6 < this.R.a.length) ? this.R.a[i6].a[i5].a[i4].a[i3].a[i2].a[i1] : ((i5 < this.Y.a.length) ? this.Y.a[i5].a[i4].a[i3].a[i2].a[i1] : ((i4 < this.X.a.length) ? this.X.a[i4].a[i3].a[i2].a[i1] : ((i3 < this.W.a.length) ? this.W.a[i3].a[i2].a[i1] : ((i2 < this.V.a.length) ? this.V.a[i2].a[i1] : this.i.a[i1])))));
    } else if ((index >= this.ad)) {
      var io$2 = ((index - this.ad) | 0);
      return this.an.a[((io$2 >>> 20) | 0)].a[(31 & ((io$2 >>> 15) | 0))].a[(31 & ((io$2 >>> 10) | 0))].a[(31 & ((io$2 >>> 5) | 0))].a[(31 & io$2)];
    } else if ((index >= this.aj)) {
      var io$3 = ((index - this.aj) | 0);
      return this.am.a[((io$3 >>> 15) | 0)].a[(31 & ((io$3 >>> 10) | 0))].a[(31 & ((io$3 >>> 5) | 0))].a[(31 & io$3)];
    } else if ((index >= this.at)) {
      var io$4 = ((index - this.at) | 0);
      return this.al.a[((io$4 >>> 10) | 0)].a[(31 & ((io$4 >>> 5) | 0))].a[(31 & io$4)];
    } else if ((index >= this.aG)) {
      var io$5 = ((index - this.aG) | 0);
      return this.ak.a[((io$5 >>> 5) | 0)].a[(31 & io$5)];
    } else {
      return this.e.a[index];
    }
  } else {
    throw this.bi(index);
  }
});
$p.er = (function(index, elem) {
  if (((index >= 0) && (index < this.j))) {
    if ((index >= this.ae)) {
      var io = ((index - this.ae) | 0);
      var i6 = ((io >>> 25) | 0);
      var i5 = (31 & ((io >>> 20) | 0));
      var i4 = (31 & ((io >>> 15) | 0));
      var i3 = (31 & ((io >>> 10) | 0));
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      if ((i6 < this.R.a.length)) {
        var a6 = this.R;
        var a6c = a6.h();
        var a5 = a6c.a[i6];
        var a5c = a5.h();
        var a4 = a5c.a[i5];
        var a4c = a4.h();
        var a3 = a4c.a[i4];
        var a3c = a3.h();
        var a2 = a3c.a[i3];
        var a2c = a2.h();
        var a1 = a2c.a[i2];
        var a1c = a1.h();
        a1c.a[i1] = elem;
        a2c.a[i2] = a1c;
        a3c.a[i3] = a2c;
        a4c.a[i4] = a3c;
        a5c.a[i5] = a4c;
        a6c.a[i6] = a5c;
        return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, a6c, this.Y, this.X, this.W, this.V, this.i, this.j);
      } else if ((i5 < this.Y.a.length)) {
        var a5$1 = this.Y;
        var a5c$1 = a5$1.h();
        var a4$1 = a5c$1.a[i5];
        var a4c$1 = a4$1.h();
        var a3$1 = a4c$1.a[i4];
        var a3c$1 = a3$1.h();
        var a2$1 = a3c$1.a[i3];
        var a2c$1 = a2$1.h();
        var a1$1 = a2c$1.a[i2];
        var a1c$1 = a1$1.h();
        a1c$1.a[i1] = elem;
        a2c$1.a[i2] = a1c$1;
        a3c$1.a[i3] = a2c$1;
        a4c$1.a[i4] = a3c$1;
        a5c$1.a[i5] = a4c$1;
        return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, a5c$1, this.X, this.W, this.V, this.i, this.j);
      } else if ((i4 < this.X.a.length)) {
        var a4$2 = this.X;
        var a4c$2 = a4$2.h();
        var a3$2 = a4c$2.a[i4];
        var a3c$2 = a3$2.h();
        var a2$2 = a3c$2.a[i3];
        var a2c$2 = a2$2.h();
        var a1$2 = a2c$2.a[i2];
        var a1c$2 = a1$2.h();
        a1c$2.a[i1] = elem;
        a2c$2.a[i2] = a1c$2;
        a3c$2.a[i3] = a2c$2;
        a4c$2.a[i4] = a3c$2;
        return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, this.Y, a4c$2, this.W, this.V, this.i, this.j);
      } else if ((i3 < this.W.a.length)) {
        var a3$3 = this.W;
        var a3c$3 = a3$3.h();
        var a2$3 = a3c$3.a[i3];
        var a2c$3 = a2$3.h();
        var a1$3 = a2c$3.a[i2];
        var a1c$3 = a1$3.h();
        a1c$3.a[i1] = elem;
        a2c$3.a[i2] = a1c$3;
        a3c$3.a[i3] = a2c$3;
        return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, this.Y, this.X, a3c$3, this.V, this.i, this.j);
      } else if ((i2 < this.V.a.length)) {
        var a2$4 = this.V;
        var a2c$4 = a2$4.h();
        var a1$4 = a2c$4.a[i2];
        var a1c$4 = a1$4.h();
        a1c$4.a[i1] = elem;
        a2c$4.a[i2] = a1c$4;
        return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, this.Y, this.X, this.W, a2c$4, this.i, this.j);
      } else {
        var a1$5 = this.i;
        var a1c$5 = a1$5.h();
        a1c$5.a[i1] = elem;
        return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, this.Y, this.X, this.W, this.V, a1c$5, this.j);
      }
    } else if ((index >= this.ad)) {
      var io$2 = ((index - this.ad) | 0);
      var a5$2 = this.an;
      var idx5 = ((io$2 >>> 20) | 0);
      var idx4 = (31 & ((io$2 >>> 15) | 0));
      var idx3 = (31 & ((io$2 >>> 10) | 0));
      var idx2 = (31 & ((io$2 >>> 5) | 0));
      var idx1 = (31 & io$2);
      var a5c$2 = a5$2.h();
      var a4$3 = a5c$2.a[idx5];
      var a4c$3 = a4$3.h();
      var a3$4 = a4c$3.a[idx4];
      var a3c$4 = a3$4.h();
      var a2$5 = a3c$4.a[idx3];
      var a2c$5 = a2$5.h();
      var a1$6 = a2c$5.a[idx2];
      var a1c$6 = a1$6.h();
      a1c$6.a[idx1] = elem;
      a2c$5.a[idx2] = a1c$6;
      a3c$4.a[idx3] = a2c$5;
      a4c$3.a[idx4] = a3c$4;
      a5c$2.a[idx5] = a4c$3;
      return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, a5c$2, this.ae, this.R, this.Y, this.X, this.W, this.V, this.i, this.j);
    } else if ((index >= this.aj)) {
      var io$3 = ((index - this.aj) | 0);
      var a4$4 = this.am;
      var idx4$1 = ((io$3 >>> 15) | 0);
      var idx3$1 = (31 & ((io$3 >>> 10) | 0));
      var idx2$1 = (31 & ((io$3 >>> 5) | 0));
      var idx1$1 = (31 & io$3);
      var a4c$4 = a4$4.h();
      var a3$5 = a4c$4.a[idx4$1];
      var a3c$5 = a3$5.h();
      var a2$6 = a3c$5.a[idx3$1];
      var a2c$6 = a2$6.h();
      var a1$7 = a2c$6.a[idx2$1];
      var a1c$7 = a1$7.h();
      a1c$7.a[idx1$1] = elem;
      a2c$6.a[idx2$1] = a1c$7;
      a3c$5.a[idx3$1] = a2c$6;
      a4c$4.a[idx4$1] = a3c$5;
      return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, a4c$4, this.ad, this.an, this.ae, this.R, this.Y, this.X, this.W, this.V, this.i, this.j);
    } else if ((index >= this.at)) {
      var io$4 = ((index - this.at) | 0);
      var a3$6 = this.al;
      var idx3$2 = ((io$4 >>> 10) | 0);
      var idx2$2 = (31 & ((io$4 >>> 5) | 0));
      var idx1$2 = (31 & io$4);
      var a3c$6 = a3$6.h();
      var a2$7 = a3c$6.a[idx3$2];
      var a2c$7 = a2$7.h();
      var a1$8 = a2c$7.a[idx2$2];
      var a1c$8 = a1$8.h();
      a1c$8.a[idx1$2] = elem;
      a2c$7.a[idx2$2] = a1c$8;
      a3c$6.a[idx3$2] = a2c$7;
      return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, a3c$6, this.aj, this.am, this.ad, this.an, this.ae, this.R, this.Y, this.X, this.W, this.V, this.i, this.j);
    } else if ((index >= this.aG)) {
      var io$5 = ((index - this.aG) | 0);
      var a2$8 = this.ak;
      var idx2$3 = ((io$5 >>> 5) | 0);
      var idx1$3 = (31 & io$5);
      var a2c$8 = a2$8.h();
      var a1$9 = a2c$8.a[idx2$3];
      var a1c$9 = a1$9.h();
      a1c$9.a[idx1$3] = elem;
      a2c$8.a[idx2$3] = a1c$9;
      return new $c_sci_Vector6(this.e, this.aG, a2c$8, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, this.Y, this.X, this.W, this.V, this.i, this.j);
    } else {
      var a1$10 = this.e;
      var a1c$10 = a1$10.h();
      a1c$10.a[index] = elem;
      return new $c_sci_Vector6(a1c$10, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, this.Y, this.X, this.W, this.V, this.i, this.j);
    }
  } else {
    throw this.bi(index);
  }
});
$p.dM = (function(elem) {
  if ((this.i.a.length < 32)) {
    var suffix1$79 = $m_sci_VectorStatics$().go(this.i, elem);
    var length0$79 = ((1 + this.j) | 0);
    return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, this.Y, this.X, this.W, this.V, suffix1$79, length0$79);
  } else if ((this.V.a.length < 31)) {
    var suffix2$63 = $m_sci_VectorStatics$().F(this.V, this.i);
    var a = new $ac_O(1);
    a.a[0] = elem;
    var length0$80 = ((1 + this.j) | 0);
    return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, this.Y, this.X, this.W, suffix2$63, a, length0$80);
  } else if ((this.W.a.length < 31)) {
    var suffix3$52 = $m_sci_VectorStatics$().F(this.W, $m_sci_VectorStatics$().F(this.V, this.i));
    var a$1 = new $ac_O(1);
    a$1.a[0] = elem;
    var length0$81 = ((1 + this.j) | 0);
    return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, this.Y, this.X, suffix3$52, $m_sci_VectorStatics$().ao, a$1, length0$81);
  } else if ((this.X.a.length < 31)) {
    var suffix4$36 = $m_sci_VectorStatics$().F(this.X, $m_sci_VectorStatics$().F(this.W, $m_sci_VectorStatics$().F(this.V, this.i)));
    var a$2 = new $ac_O(1);
    a$2.a[0] = elem;
    var length0$82 = ((1 + this.j) | 0);
    return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, this.Y, suffix4$36, $m_sci_VectorStatics$().by, $m_sci_VectorStatics$().ao, a$2, length0$82);
  } else if ((this.Y.a.length < 31)) {
    var suffix5$15 = $m_sci_VectorStatics$().F(this.Y, $m_sci_VectorStatics$().F(this.X, $m_sci_VectorStatics$().F(this.W, $m_sci_VectorStatics$().F(this.V, this.i))));
    var a$3 = new $ac_O(1);
    a$3.a[0] = elem;
    var length0$83 = ((1 + this.j) | 0);
    return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, this.R, suffix5$15, $m_sci_VectorStatics$().dh, $m_sci_VectorStatics$().by, $m_sci_VectorStatics$().ao, a$3, length0$83);
  } else if ((this.R.a.length < 62)) {
    var data6$16 = $m_sci_VectorStatics$().F(this.R, $m_sci_VectorStatics$().F(this.Y, $m_sci_VectorStatics$().F(this.X, $m_sci_VectorStatics$().F(this.W, $m_sci_VectorStatics$().F(this.V, this.i)))));
    var a$4 = new $ac_O(1);
    a$4.a[0] = elem;
    var length0$84 = ((1 + this.j) | 0);
    return new $c_sci_Vector6(this.e, this.aG, this.ak, this.at, this.al, this.aj, this.am, this.ad, this.an, this.ae, data6$16, $m_sci_VectorStatics$().h9, $m_sci_VectorStatics$().dh, $m_sci_VectorStatics$().by, $m_sci_VectorStatics$().ao, a$4, length0$84);
  } else {
    throw $ct_jl_IllegalArgumentException__(new $c_jl_IllegalArgumentException());
  }
});
$p.cJ = (function(elem) {
  if ((this.aG < 32)) {
    var prefix1$81 = $m_sci_VectorStatics$().gp(elem, this.e);
    var len1$75 = ((1 + this.aG) | 0);
    var len12$68 = ((1 + this.at) | 0);
    var len123$56 = ((1 + this.aj) | 0);
    var len1234$39 = ((1 + this.ad) | 0);
    var len12345$17 = ((1 + this.ae) | 0);
    var length0$85 = ((1 + this.j) | 0);
    return new $c_sci_Vector6(prefix1$81, len1$75, this.ak, len12$68, this.al, len123$56, this.am, len1234$39, this.an, len12345$17, this.R, this.Y, this.X, this.W, this.V, this.i, length0$85);
  } else if ((this.at < 1024)) {
    var a = new $ac_O(1);
    a.a[0] = elem;
    var prefix2$69 = $m_sci_VectorStatics$().B(this.e, this.ak);
    var len12$69 = ((1 + this.at) | 0);
    var len123$57 = ((1 + this.aj) | 0);
    var len1234$40 = ((1 + this.ad) | 0);
    var len12345$18 = ((1 + this.ae) | 0);
    var length0$86 = ((1 + this.j) | 0);
    return new $c_sci_Vector6(a, 1, prefix2$69, len12$69, this.al, len123$57, this.am, len1234$40, this.an, len12345$18, this.R, this.Y, this.X, this.W, this.V, this.i, length0$86);
  } else if ((this.aj < 32768)) {
    var a$1 = new $ac_O(1);
    a$1.a[0] = elem;
    var prefix3$58 = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.ak), this.al);
    var len123$58 = ((1 + this.aj) | 0);
    var len1234$41 = ((1 + this.ad) | 0);
    var len12345$19 = ((1 + this.ae) | 0);
    var length0$87 = ((1 + this.j) | 0);
    var prefix4$41 = this.am;
    var prefix5$19 = this.an;
    var data6$19 = this.R;
    var suffix5$18 = this.Y;
    var suffix4$39 = this.X;
    var suffix3$55 = this.W;
    var suffix2$66 = this.V;
    var suffix1$87 = this.i;
    return new $c_sci_Vector6(a$1, 1, $m_sci_VectorStatics$().ao, 1, prefix3$58, len123$58, prefix4$41, len1234$41, prefix5$19, len12345$19, data6$19, suffix5$18, suffix4$39, suffix3$55, suffix2$66, suffix1$87, length0$87);
  } else if ((this.ad < 1048576)) {
    var a$2 = new $ac_O(1);
    a$2.a[0] = elem;
    var prefix4$42 = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.ak), this.al), this.am);
    var len1234$42 = ((1 + this.ad) | 0);
    var len12345$20 = ((1 + this.ae) | 0);
    var length0$88 = ((1 + this.j) | 0);
    var prefix5$20 = this.an;
    var data6$20 = this.R;
    var suffix5$19 = this.Y;
    var suffix4$40 = this.X;
    var suffix3$56 = this.W;
    var suffix2$67 = this.V;
    var suffix1$88 = this.i;
    return new $c_sci_Vector6(a$2, 1, $m_sci_VectorStatics$().ao, 1, $m_sci_VectorStatics$().by, 1, prefix4$42, len1234$42, prefix5$20, len12345$20, data6$20, suffix5$19, suffix4$40, suffix3$56, suffix2$67, suffix1$88, length0$88);
  } else if ((this.ae < 33554432)) {
    var a$3 = new $ac_O(1);
    a$3.a[0] = elem;
    var prefix5$21 = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.ak), this.al), this.am), this.an);
    var len12345$21 = ((1 + this.ae) | 0);
    var length0$89 = ((1 + this.j) | 0);
    var data6$21 = this.R;
    var suffix5$20 = this.Y;
    var suffix4$41 = this.X;
    var suffix3$57 = this.W;
    var suffix2$68 = this.V;
    var suffix1$89 = this.i;
    return new $c_sci_Vector6(a$3, 1, $m_sci_VectorStatics$().ao, 1, $m_sci_VectorStatics$().by, 1, $m_sci_VectorStatics$().dh, 1, prefix5$21, len12345$21, data6$21, suffix5$20, suffix4$41, suffix3$57, suffix2$68, suffix1$89, length0$89);
  } else if ((this.R.a.length < 62)) {
    var a$4 = new $ac_O(1);
    a$4.a[0] = elem;
    var data6$22 = $m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B($m_sci_VectorStatics$().B(this.e, this.ak), this.al), this.am), this.an), this.R);
    var length0$90 = ((1 + this.j) | 0);
    var suffix5$21 = this.Y;
    var suffix4$42 = this.X;
    var suffix3$58 = this.W;
    var suffix2$69 = this.V;
    var suffix1$90 = this.i;
    return new $c_sci_Vector6(a$4, 1, $m_sci_VectorStatics$().ao, 1, $m_sci_VectorStatics$().by, 1, $m_sci_VectorStatics$().dh, 1, $m_sci_VectorStatics$().h9, 1, data6$22, suffix5$21, suffix4$42, suffix3$58, suffix2$69, suffix1$90, length0$90);
  } else {
    throw $ct_jl_IllegalArgumentException__(new $c_jl_IllegalArgumentException());
  }
});
$p.dq = (function(f) {
  var prefix1$87 = $m_sci_VectorStatics$().d5(this.e, f);
  var prefix2$70 = $m_sci_VectorStatics$().aJ(2, this.ak, f);
  var prefix3$59 = $m_sci_VectorStatics$().aJ(3, this.al, f);
  var prefix4$43 = $m_sci_VectorStatics$().aJ(4, this.am, f);
  var prefix5$22 = $m_sci_VectorStatics$().aJ(5, this.an, f);
  var data6$23 = $m_sci_VectorStatics$().aJ(6, this.R, f);
  var suffix5$22 = $m_sci_VectorStatics$().aJ(5, this.Y, f);
  var suffix4$43 = $m_sci_VectorStatics$().aJ(4, this.X, f);
  var suffix3$59 = $m_sci_VectorStatics$().aJ(3, this.W, f);
  var suffix2$70 = $m_sci_VectorStatics$().aJ(2, this.V, f);
  var suffix1$91 = $m_sci_VectorStatics$().d5(this.i, f);
  return new $c_sci_Vector6(prefix1$87, this.aG, prefix2$70, this.at, prefix3$59, this.aj, prefix4$43, this.ad, prefix5$22, this.ae, data6$23, suffix5$22, suffix4$43, suffix3$59, suffix2$70, suffix1$91, this.j);
});
$p.dY = (function() {
  return 11;
});
$p.dX = (function(idx) {
  switch (idx) {
    case 0: {
      return this.e;
      break;
    }
    case 1: {
      return this.ak;
      break;
    }
    case 2: {
      return this.al;
      break;
    }
    case 3: {
      return this.am;
      break;
    }
    case 4: {
      return this.an;
      break;
    }
    case 5: {
      return this.R;
      break;
    }
    case 6: {
      return this.Y;
      break;
    }
    case 7: {
      return this.X;
      break;
    }
    case 8: {
      return this.W;
      break;
    }
    case 9: {
      return this.V;
      break;
    }
    case 10: {
      return this.i;
      break;
    }
    default: {
      throw new $c_s_MatchError(idx);
    }
  }
});
$p.d = (function(v1) {
  var index = (v1 | 0);
  if (((index >= 0) && (index < this.j))) {
    var io = ((index - this.ae) | 0);
    if ((io >= 0)) {
      var i6 = ((io >>> 25) | 0);
      var i5 = (31 & ((io >>> 20) | 0));
      var i4 = (31 & ((io >>> 15) | 0));
      var i3 = (31 & ((io >>> 10) | 0));
      var i2 = (31 & ((io >>> 5) | 0));
      var i1 = (31 & io);
      return ((i6 < this.R.a.length) ? this.R.a[i6].a[i5].a[i4].a[i3].a[i2].a[i1] : ((i5 < this.Y.a.length) ? this.Y.a[i5].a[i4].a[i3].a[i2].a[i1] : ((i4 < this.X.a.length) ? this.X.a[i4].a[i3].a[i2].a[i1] : ((i3 < this.W.a.length) ? this.W.a[i3].a[i2].a[i1] : ((i2 < this.V.a.length) ? this.V.a[i2].a[i1] : this.i.a[i1])))));
    } else if ((index >= this.ad)) {
      var io$2 = ((index - this.ad) | 0);
      return this.an.a[((io$2 >>> 20) | 0)].a[(31 & ((io$2 >>> 15) | 0))].a[(31 & ((io$2 >>> 10) | 0))].a[(31 & ((io$2 >>> 5) | 0))].a[(31 & io$2)];
    } else if ((index >= this.aj)) {
      var io$3 = ((index - this.aj) | 0);
      return this.am.a[((io$3 >>> 15) | 0)].a[(31 & ((io$3 >>> 10) | 0))].a[(31 & ((io$3 >>> 5) | 0))].a[(31 & io$3)];
    } else if ((index >= this.at)) {
      var io$4 = ((index - this.at) | 0);
      return this.al.a[((io$4 >>> 10) | 0)].a[(31 & ((io$4 >>> 5) | 0))].a[(31 & io$4)];
    } else if ((index >= this.aG)) {
      var io$5 = ((index - this.aG) | 0);
      return this.ak.a[((io$5 >>> 5) | 0)].a[(31 & io$5)];
    } else {
      return this.e.a[index];
    }
  } else {
    throw this.bi(index);
  }
});
$p.bp = (function(elem) {
  return this.cJ(elem);
});
$p.aa = (function(f) {
  return this.dq(f);
});
var $d_sci_Vector6 = new $TypeData().i($c_sci_Vector6, "scala.collection.immutable.Vector6", ({
  gC: 1,
  ap: 1,
  aq: 1,
  ag: 1,
  z: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  q: 1,
  y: 1,
  x: 1,
  n: 1,
  t: 1,
  G: 1,
  A: 1,
  l: 1,
  u: 1,
  D: 1,
  a: 1,
  X: 1
}));
function $ct_scm_StringBuilder__jl_StringBuilder__($thiz, underlying) {
  $thiz.bC = underlying;
  return $thiz;
}
function $ct_scm_StringBuilder__($thiz) {
  $ct_scm_StringBuilder__jl_StringBuilder__($thiz, $ct_jl_StringBuilder__(new $c_jl_StringBuilder()));
  return $thiz;
}
/** @constructor */
function $c_scm_StringBuilder() {
  this.bC = null;
}
$p = $c_scm_StringBuilder.prototype = new $h_scm_AbstractSeq();
$p.constructor = $c_scm_StringBuilder;
/** @constructor */
function $h_scm_StringBuilder() {
}
$h_scm_StringBuilder.prototype = $p;
$p.b7 = (function(elems) {
  return $f_scm_Growable__addAll__sc_IterableOnce__scm_Growable(this, elems);
});
$p.aZ = (function(size) {
});
$p.c = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), new $c_sc_IndexedSeqView$Id(this));
});
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), new $c_sc_IndexedSeqView$Id(this));
});
$p.d6 = (function() {
  return new $c_sc_IndexedSeqView$Reverse(this);
});
$p.aa = (function(f) {
  return $f_sc_IndexedSeqOps__map__F1__O(this, f);
});
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var x = this.bC.l();
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.bl = (function() {
  return "IndexedSeq";
});
$p.cI = (function() {
  return $ct_scm_GrowableBuilder__scm_Growable__(new $c_scm_GrowableBuilder(), $ct_scm_StringBuilder__(new $c_scm_StringBuilder()));
});
$p.l = (function() {
  return this.bC.l();
});
$p.q = (function() {
  return this.bC.l();
});
$p.pN = (function(x) {
  var this$1 = this.bC;
  var str = ("" + $cToS(x));
  this$1.w = (this$1.w + str);
  return this;
});
$p.t = (function() {
  return this.bC.w;
});
$p.nP = (function(xs) {
  if ((xs instanceof $c_sci_WrappedString)) {
    var this$3 = this.bC;
    var str = xs.bY;
    this$3.w = (("" + this$3.w) + str);
  } else if ((xs instanceof $c_scm_ArraySeq$ofChar)) {
    this.bC.nO(xs.cB);
  } else if ((xs instanceof $c_scm_StringBuilder)) {
    var this$4 = this.bC;
    var s = xs.bC;
    this$4.w = (("" + this$4.w) + s);
  } else {
    var ks = xs.q();
    if ((ks !== 0)) {
      var b = this.bC;
      if ((ks > 0)) {
        b.l();
      }
      var it = xs.c();
      while (it.k()) {
        var c = $uC(it.f());
        var str$1 = ("" + $cToS(c));
        b.w = (b.w + str$1);
      }
    }
  }
  return this;
});
$p.g = (function() {
  return (this.bC.l() === 0);
});
$p.r = (function(i) {
  return $bC(this.bC.nV(i));
});
$p.d = (function(v1) {
  var i = (v1 | 0);
  return $bC(this.bC.nV(i));
});
$p.gr = (function(coll) {
  return $ct_scm_StringBuilder__(new $c_scm_StringBuilder()).nP(coll);
});
$p.en = (function(coll) {
  return $ct_scm_StringBuilder__(new $c_scm_StringBuilder()).nP(coll);
});
$p.aq = (function(elem) {
  return this.pN($uC(elem));
});
$p.aU = (function() {
  return this.bC.w;
});
$p.aH = (function() {
  return $m_scm_IndexedSeq$();
});
function $isArrayOf_scm_StringBuilder(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cI)));
}
var $d_scm_StringBuilder = new $TypeData().i($c_scm_StringBuilder, "scala.collection.mutable.StringBuilder", ({
  cI: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  H: 1,
  I: 1,
  K: 1,
  a7: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  aQ: 1,
  a: 1
}));
function $isArrayOf_scm_LinkedHashMap(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.hb)));
}
function $p_scm_ListBuffer__copyElems__V($thiz) {
  var buf = new $c_scm_ListBuffer().j8($thiz);
  $thiz.d1 = buf.d1;
  $thiz.ei = buf.ei;
  $thiz.iy = false;
}
function $p_scm_ListBuffer__ensureUnaliased__V($thiz) {
  $thiz.iz = ((1 + $thiz.iz) | 0);
  if ($thiz.iy) {
    $p_scm_ListBuffer__copyElems__V($thiz);
  }
}
/** @constructor */
function $c_scm_ListBuffer() {
  this.iz = 0;
  this.d1 = null;
  this.ei = null;
  this.iy = false;
  this.d2 = 0;
  this.iz = 0;
  this.d1 = $m_sci_Nil$();
  this.ei = null;
  this.iy = false;
  this.d2 = 0;
}
$p = $c_scm_ListBuffer.prototype = new $h_scm_AbstractBuffer();
$p.constructor = $c_scm_ListBuffer;
/** @constructor */
function $h_scm_ListBuffer() {
}
$h_scm_ListBuffer.prototype = $p;
$p.cb = (function(f) {
  return $f_sc_StrictOptimizedSeqOps__distinctBy__F1__O(this, f);
});
$p.aZ = (function(size) {
});
$p.c = (function() {
  return new $c_scm_MutationTracker$CheckedIterator(this.d1.c(), new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => this.iz)));
});
$p.ep = (function() {
  return $m_scm_ListBuffer$();
});
$p.r = (function(i) {
  return $f_sc_LinearSeqOps__apply__I__O(this.d1, i);
});
$p.l = (function() {
  return this.d2;
});
$p.q = (function() {
  return this.d2;
});
$p.g = (function() {
  return (this.d2 === 0);
});
$p.c2 = (function() {
  this.iy = (!this.g());
  return this.d1;
});
$p.gm = (function(elem) {
  $p_scm_ListBuffer__ensureUnaliased__V(this);
  var last1 = new $c_sci_$colon$colon(elem, $m_sci_Nil$());
  if ((this.d2 === 0)) {
    this.d1 = last1;
  } else {
    var x$proxy2 = this.ei;
    if ((x$proxy2 === null)) {
      $m_sr_Scala3RunTime$().bV();
    }
    x$proxy2.K = last1;
  }
  this.ei = last1;
  this.d2 = ((1 + this.d2) | 0);
  return this;
});
$p.j8 = (function(xs) {
  var it = xs.c();
  if (it.k()) {
    var len = 1;
    var last0 = new $c_sci_$colon$colon(it.f(), $m_sci_Nil$());
    this.d1 = last0;
    while (it.k()) {
      var last1 = new $c_sci_$colon$colon(it.f(), $m_sci_Nil$());
      last0.K = last1;
      last0 = last1;
      len = ((1 + len) | 0);
    }
    this.d2 = len;
    this.ei = last0;
  }
  return this;
});
$p.pH = (function(xs) {
  var it = xs.c();
  if (it.k()) {
    var fresh = new $c_scm_ListBuffer().j8(it);
    $p_scm_ListBuffer__ensureUnaliased__V(this);
    if ((this.d2 === 0)) {
      this.d1 = fresh.d1;
    } else {
      var x$proxy3 = this.ei;
      if ((x$proxy3 === null)) {
        $m_sr_Scala3RunTime$().bV();
      }
      x$proxy3.K = fresh.d1;
    }
    this.ei = fresh.ei;
    this.d2 = ((this.d2 + fresh.d2) | 0);
  }
  return this;
});
$p.bl = (function() {
  return "ListBuffer";
});
$p.aH = (function() {
  return $m_scm_ListBuffer$();
});
$p.d = (function(v1) {
  var i = (v1 | 0);
  return $f_sc_LinearSeqOps__apply__I__O(this.d1, i);
});
$p.aU = (function() {
  return this.c2();
});
$p.aq = (function(elem) {
  return this.gm(elem);
});
$p.b7 = (function(elems) {
  return this.pH(elems);
});
function $isArrayOf_scm_ListBuffer(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.cH)));
}
var $d_scm_ListBuffer = new $TypeData().i($c_scm_ListBuffer, "scala.collection.mutable.ListBuffer", ({
  cH: 1,
  b3: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  H: 1,
  I: 1,
  aG: 1,
  b4: 1,
  l: 1,
  u: 1,
  K: 1,
  a7: 1,
  a: 1,
  X: 1
}));
function $ct_scm_ArrayBuffer__AO__I__($thiz, initialElements, initialSize) {
  $thiz.gi = 0;
  $thiz.dI = initialElements;
  $thiz.bz = initialSize;
  return $thiz;
}
function $ct_scm_ArrayBuffer__($thiz) {
  $ct_scm_ArrayBuffer__AO__I__($thiz, new $ac_O(16), 0);
  return $thiz;
}
function $p_scm_ArrayBuffer__foldl__I__I__O__F2__O($thiz, start, end, z, op) {
  var z$tailLocal1 = z;
  var start$tailLocal1 = start;
  while (true) {
    if ((start$tailLocal1 === end)) {
      return z$tailLocal1;
    } else {
      var start$tailLocal1$tmp1 = ((1 + start$tailLocal1) | 0);
      var z$tailLocal1$tmp1 = op.ca(z$tailLocal1, $thiz.dI.a[start$tailLocal1]);
      start$tailLocal1 = start$tailLocal1$tmp1;
      z$tailLocal1 = z$tailLocal1$tmp1;
    }
  }
}
/** @constructor */
function $c_scm_ArrayBuffer() {
  this.gi = 0;
  this.dI = null;
  this.bz = 0;
}
$p = $c_scm_ArrayBuffer.prototype = new $h_scm_AbstractBuffer();
$p.constructor = $c_scm_ArrayBuffer;
/** @constructor */
function $h_scm_ArrayBuffer() {
}
$h_scm_ArrayBuffer.prototype = $p;
$p.c = (function() {
  return this.p8().c();
});
$p.bj = (function() {
  return this.p8().bj();
});
$p.d6 = (function() {
  return new $c_sc_IndexedSeqView$Reverse(this);
});
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var x = this.bz;
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.aa = (function(f) {
  return $f_sc_StrictOptimizedIterableOps__map__F1__O(this, f);
});
$p.cb = (function(f) {
  return $f_sc_StrictOptimizedSeqOps__distinctBy__F1__O(this, f);
});
$p.q = (function() {
  return this.bz;
});
$p.kz = (function(n) {
  this.dI = $m_scm_ArrayBuffer$().oP(this.dI, this.bz, n);
});
$p.aZ = (function(size) {
  if (((size > this.bz) && (size >= 1))) {
    this.kz(size);
  }
});
$p.r = (function(n) {
  var hi = ((1 + n) | 0);
  if ((n < 0)) {
    throw $m_scg_CommonErrors$().kQ(n, ((this.bz - 1) | 0));
  }
  if ((hi > this.bz)) {
    throw $m_scg_CommonErrors$().kQ(((hi - 1) | 0), ((this.bz - 1) | 0));
  }
  return this.dI.a[n];
});
$p.l = (function() {
  return this.bz;
});
$p.p8 = (function() {
  return new $c_scm_ArrayBufferView(this, new $c_sr_AbstractFunction0_$$Lambda$07eded5776954a9c145e92c329afd52873ad179c((() => this.gi)));
});
$p.ep = (function() {
  return $m_scm_ArrayBuffer$();
});
$p.pT = (function(elem) {
  this.gi = ((1 + this.gi) | 0);
  var newSize = ((1 + this.bz) | 0);
  if ((this.dI.a.length <= ((newSize - 1) | 0))) {
    this.kz(newSize);
  }
  this.bz = newSize;
  this.dI.a[((newSize - 1) | 0)] = elem;
  return this;
});
$p.nI = (function(elems) {
  if ((elems instanceof $c_scm_ArrayBuffer)) {
    var elemsLength = elems.bz;
    if ((elemsLength > 0)) {
      this.gi = ((1 + this.gi) | 0);
      this.kz(((this.bz + elemsLength) | 0));
      $m_s_Array$().bT(elems.dI, 0, this.dI, this.bz, elemsLength);
      this.bz = ((this.bz + elemsLength) | 0);
    }
  } else {
    $f_scm_Growable__addAll__sc_IterableOnce__scm_Growable(this, elems);
  }
  return this;
});
$p.bl = (function() {
  return "ArrayBuffer";
});
$p.bc = (function(xs, start, len) {
  var srcLen = this.bz;
  var destLen = $m_jl_reflect_Array$().b8(xs);
  var limit = ((len < srcLen) ? len : srcLen);
  var capacity = ((start < 0) ? destLen : ((destLen - start) | 0));
  var total = ((capacity < limit) ? capacity : limit);
  var copied = ((total < 0) ? 0 : total);
  if ((copied > 0)) {
    $m_s_Array$().bT(this.dI, 0, xs, start, copied);
  }
  return copied;
});
$p.d4 = (function(z, op) {
  return $p_scm_ArrayBuffer__foldl__I__I__O__F2__O(this, 0, this.bz, z, op);
});
$p.d = (function(v1) {
  return this.r((v1 | 0));
});
$p.aH = (function() {
  return $m_scm_ArrayBuffer$();
});
$p.aq = (function(elem) {
  return this.pT(elem);
});
$p.b7 = (function(elems) {
  return this.nI(elems);
});
function $isArrayOf_scm_ArrayBuffer(obj, depth) {
  return (!(!(((obj && obj.$classData) && (obj.$classData.D === depth)) && obj.$classData.B.n.ct)));
}
var $d_scm_ArrayBuffer = new $TypeData().i($c_scm_ArrayBuffer, "scala.collection.mutable.ArrayBuffer", ({
  ct: 1,
  b3: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  H: 1,
  I: 1,
  aG: 1,
  b4: 1,
  n: 1,
  t: 1,
  Z: 1,
  Y: 1,
  cG: 1,
  l: 1,
  u: 1,
  a: 1,
  X: 1
}));
function $ct_sjs_js_WrappedArray__sjs_js_Array__($thiz, array) {
  $thiz.ek = array;
  return $thiz;
}
function $ct_sjs_js_WrappedArray__($thiz) {
  $ct_sjs_js_WrappedArray__sjs_js_Array__($thiz, []);
  return $thiz;
}
/** @constructor */
function $c_sjs_js_WrappedArray() {
  this.ek = null;
}
$p = $c_sjs_js_WrappedArray.prototype = new $h_scm_AbstractBuffer();
$p.constructor = $c_sjs_js_WrappedArray;
/** @constructor */
function $h_sjs_js_WrappedArray() {
}
$h_sjs_js_WrappedArray.prototype = $p;
$p.aZ = (function(size) {
});
$p.bl = (function() {
  return "IndexedSeq";
});
$p.c = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewIterator(), new $c_sc_IndexedSeqView$Id(this));
});
$p.bj = (function() {
  return $ct_sc_IndexedSeqView$IndexedSeqViewReverseIterator__sc_IndexedSeqView__(new $c_sc_IndexedSeqView$IndexedSeqViewReverseIterator(), new $c_sc_IndexedSeqView$Id(this));
});
$p.d6 = (function() {
  return new $c_sc_IndexedSeqView$Reverse(this);
});
$p.aa = (function(f) {
  return $f_sc_IndexedSeqOps__map__F1__O(this, f);
});
$p.m = (function() {
  return $f_sc_IndexedSeqOps__head__O(this);
});
$p.aI = (function(len) {
  var x = (this.ek.length | 0);
  return ((x === len) ? 0 : ((x < len) ? (-1) : 1));
});
$p.cb = (function(f) {
  return $f_sc_StrictOptimizedSeqOps__distinctBy__F1__O(this, f);
});
$p.ep = (function() {
  return $m_sjs_js_WrappedArray$();
});
$p.r = (function(index) {
  return this.ek[index];
});
$p.l = (function() {
  return (this.ek.length | 0);
});
$p.q = (function() {
  return (this.ek.length | 0);
});
$p.bK = (function() {
  return "WrappedArray";
});
$p.aU = (function() {
  return this;
});
$p.aq = (function(elem) {
  this.ek.push(elem);
  return this;
});
$p.d = (function(v1) {
  var index = (v1 | 0);
  return this.ek[index];
});
$p.aH = (function() {
  return $m_sjs_js_WrappedArray$();
});
var $d_sjs_js_WrappedArray = new $TypeData().i($c_sjs_js_WrappedArray, "scala.scalajs.js.WrappedArray", ({
  iY: 1,
  b3: 1,
  R: 1,
  s: 1,
  h: 1,
  b: 1,
  c: 1,
  g: 1,
  f: 1,
  e: 1,
  i: 1,
  j: 1,
  k: 1,
  d: 1,
  o: 1,
  N: 1,
  E: 1,
  M: 1,
  T: 1,
  S: 1,
  H: 1,
  I: 1,
  aG: 1,
  b4: 1,
  u: 1,
  l: 1,
  Y: 1,
  n: 1,
  t: 1,
  Z: 1,
  cG: 1,
  K: 1,
  a: 1
}));
dropdownHandler = (function(arg) {
  $m_Ldotty_tools_scaladoc_DropdownHandler$package$().qw(arg);
});
filterFunction = (function() {
  $m_Ldotty_tools_scaladoc_DropdownHandler$package$().qJ();
});
$s_Ldotty_tools_scaladoc_Main__main__AT__V(new ($d_T.r().C)([]));
}).call(this);
//# sourceMappingURL=scaladoc-js-main-opt.js.map
