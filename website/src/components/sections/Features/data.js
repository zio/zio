import {
  FaGaugeHigh,
  FaShieldHalved,
  FaCodeFork,
  FaBolt,
  FaRecycle,
  FaFlask,
  FaArrowsRotate,
  FaPuzzlePiece,
} from 'react-icons/fa6';

export const features = [
  {
    title: 'High-performance',
    content: 'Build scalable applications with minimal runtime overhead',
    icon: FaGaugeHigh,
  },
  {
    title: 'Type-safe',
    content:
      'Use the full power of the Scala compiler to catch bugs at compile time',
    icon: FaShieldHalved,
  },
  {
    title: 'Concurrent',
    content:
      'Easily build concurrent apps without deadlocks, race conditions, or complexity',
    icon: FaCodeFork,
  },
  {
    title: 'Asynchronous',
    content:
      'Write sequential code that looks the same whether it’s asynchronous or synchronous',
    icon: FaBolt,
  },
  {
    title: 'Resource-safe',
    content:
      'Build apps that never leak resources (including threads!), even when they fail',
    icon: FaRecycle,
  },
  {
    title: 'Testable',
    content:
      'Inject test services into your app for fast, deterministic, and type-safe testing',
    icon: FaFlask,
  },
  {
    title: 'Resilient',
    content:
      'Build apps that never lose errors, and which respond to failure locally and flexibly',
    icon: FaArrowsRotate,
  },
  {
    title: 'Functional',
    content:
      'Rapidly compose solutions to complex problems from simple building blocks',
    icon: FaPuzzlePiece,
  },
];
