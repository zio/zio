import React from "react";
import Link from "@docusaurus/Link";

import SectionWrapper from "@site/src/components/ui/SectionWrapper";

import { testimonials } from "./data";

export default function Testimonials() {
  return (
    <SectionWrapper
      title="What People Are Saying"
      subtitle="Hear from developers who use ZIO in production"
    >
      <div className="container">
        <div className="mx-auto grid max-w-7xl grid-cols-1 gap-8 md:grid-cols-2">
          {testimonials.map((testimonial, idx) => (
            <div
              key={`testimonial-${idx}`}
              className="flex flex-col rounded-xl border border-gray-200 p-6 shadow-sm transition-shadow hover:shadow-md dark:border-gray-700"
            >
              <p className="mb-4 text-lg italic text-gray-600 dark:text-gray-300">
                &quot;{testimonial.quote}&quot;
              </p>
              <div className="mt-auto">
                <Link
                  href={testimonial.link}
                  className="font-semibold text-blue-600 hover:text-blue-800 dark:text-blue-400"
                >
                  {testimonial.author}
                </Link>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {testimonial.role}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </SectionWrapper>
  );
}
