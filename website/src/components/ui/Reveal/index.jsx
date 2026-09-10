import React, { useEffect, useRef, useState } from 'react';
import clsx from 'clsx';

/**
 * Wraps children and fades/slides them into view when scrolled near the
 * viewport. Movement is defined purely in CSS behind a
 * `prefers-reduced-motion: no-preference` guard, so reduced-motion users and
 * the no-JS/SSR fallback both see the final state with no animation.
 */
export default function Reveal({ children, className, as: Tag = 'div' }) {
  const ref = useRef(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const node = ref.current;
    if (!node || typeof IntersectionObserver === 'undefined') {
      setVisible(true);
      return undefined;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setVisible(true);
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -10% 0px' },
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return (
    <Tag
      ref={ref}
      className={clsx('reveal', visible && 'reveal--visible', className)}
    >
      {children}
    </Tag>
  );
}
