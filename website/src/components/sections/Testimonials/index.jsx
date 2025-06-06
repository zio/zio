import React, { useState } from 'react';
import { FaChevronLeft, FaChevronRight } from 'react-icons/fa';
import styles from './styles.module.css';
import { testimonials } from './data';

export default function Testimonials() {
  const [currentIndex, setCurrentIndex] = useState(0);

  const handlePrevious = () => {
    setCurrentIndex((prevIndex) => 
      prevIndex === 0 ? testimonials.length - 1 : prevIndex - 1
    );
  };

  const handleNext = () => {
    setCurrentIndex((prevIndex) => 
      prevIndex === testimonials.length - 1 ? 0 : prevIndex + 1
    );
  };

  const currentTestimonial = testimonials[currentIndex];

  return (
    <section className={styles.testimonials}>
      <div className="container">
        <h2 className={styles.title}>What People Are Saying</h2>
        <div className={styles.testimonialContainer}>
          <button 
            className={styles.navButton} 
            onClick={handlePrevious}
            aria-label="Previous testimonial"
          >
            <FaChevronLeft />
          </button>
          
          <div className={styles.testimonialCard}>
            <div className={styles.testimonialContent}>
              <p className={styles.quote}>
                "{currentTestimonial.quote}"
              </p>
              <div className={styles.author}>
                <div className={styles.authorInfo}>
                  <strong>{currentTestimonial.author}</strong>
                  <span>{currentTestimonial.role}</span>
                </div>
                <a 
                  href={currentTestimonial.tweetUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className={styles.tweetLink}
                >
                  View Tweet
                </a>
              </div>
            </div>
          </div>

          <button 
            className={styles.navButton} 
            onClick={handleNext}
            aria-label="Next testimonial"
          >
            <FaChevronRight />
          </button>
        </div>
      </div>
    </section>
  );
} 