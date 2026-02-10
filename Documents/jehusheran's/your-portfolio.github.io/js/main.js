// /assets/js/main.js - Portfolio Interactive Features
// Dark theme animations, smooth scroll, project reveals for AI/ML showcase[cite:17]

'use strict';

class PortfolioApp {
  constructor() {
    this.init();
  }

  init() {
    this.bindEvents();
    this.observeElements();
    this.updateActiveNav();
  }

  // Smooth scrolling & active nav highlighting
  bindEvents() {
    // All anchor links
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
      anchor.addEventListener('click', (e) => {
        e.preventDefault();
        const target = document.querySelector(anchor.getAttribute('href'));
        if (target) {
          target.scrollIntoView({ 
            behavior: 'smooth', 
            block: 'start' 
          });
        }
      });
    });

    // Mobile menu toggle (from header.html)
    const hamburger = document.getElementById('hamburger');
    const mobileMenu = document.getElementById('mobileMenu');
    
    if (hamburger && mobileMenu) {
      hamburger.addEventListener('click', () => {
        hamburger.classList.toggle('active');
        mobileMenu.classList.toggle('active');
      });
    }
  }

  // Intersection Observer for scroll animations
  observeElements() {
    const observerOptions = {
      threshold: 0.1,
      rootMargin: '0px 0px -50px 0px'
    };

    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('animate-in');
          
          // Stagger animation for grids (projects, skills)
          if (entry.target.classList.contains('grid')) {
            const children = entry.target.querySelectorAll('[data-delay]');
            children.forEach((child, index) => {
              child.style.animationDelay = `${index * 0.1}s`;
            });
          }
        }
      });
    }, observerOptions);

    // Observe project cards, skill sections, metrics
    document.querySelectorAll('.project-card, .skill-category, .metric-item, .gallery-item').forEach(el => {
      observer.observe(el);
    });
  }

  // Update navbar active states
  updateActiveNav() {
    window.addEventListener('scroll', () => {
      const sections = ['home', 'about', 'projects', 'skills', 'research', 'contact'];
      const scrollPos = window.scrollY + 100;

      sections.forEach(section => {
        const el = document.getElementById(section);
        const navLink = document.querySelector(`[href="#${section}"]`);
        
        if (el && navLink) {
          if (el.offsetTop <= scrollPos && el.offsetTop + el.offsetHeight > scrollPos) {
            document.querySelectorAll('.nav-link').forEach(link => {
              link.classList.remove('active');
            });
            navLink.classList.add('active');
          }
        }
      });

      // Header scroll effect
      const header = document.querySelector('.site-header');
      if (header) {
        if (window.scrollY > 100) {
          header.style.background = 'rgba(var(--card-bg), 0.98)';
          header.style.boxShadow = '0 4px 20px rgba(0,0,0,0.3)';
        } else {
          header.style.background = 'rgba(var(--card-bg), 0.95)';
          header.style.boxShadow = 'none';
        }
      }
    });
  }
}

// Project detail interactions (Smart Bus screenshots, YOLO demos)
class ProjectInteractions {
  constructor() {
    this.initImageGallery();
    this.initMetricsAnimation();
  }

  initImageGallery() {
    document.querySelectorAll('.gallery-grid').forEach(grid => {
      const images = grid.querySelectorAll('.gallery-item img');
      
      images.forEach((img, index) => {
        img.addEventListener('click', () => {
          this.openLightbox(img.src, img.alt);
        });
      });
    });
  }

  initMetricsAnimation() {
    // Animate metrics counters (92% → 0%→92%)
    document.querySelectorAll('.metric-value').forEach(metric => {
      const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            const value = metric.dataset.value || metric.textContent;
            this.animateCounter(metric, value);
            observer.unobserve(entry.target);
          }
        });
      });
      observer.observe(metric);
    });
  }

  animateCounter(element, targetValue) {
    const duration = 2000;
    const step = targetValue / (duration / 16);
    let current = 0;

    const timer = setInterval(() => {
      current += step;
      if (current >= targetValue) {
        current = targetValue;
        clearInterval(timer);
      }
      element.textContent = this.formatNumber(current);
    }, 16);
  }

  formatNumber(num) {
    if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
    if (num >= 1000) return (num / 1000).toFixed(1) + 'k';
    return num.toFixed(num % 1 === 0 ? 0 : 1) + '%';
  }

  openLightbox(src, alt) {
    const lightbox = document.createElement('div');
    lightbox.className = 'lightbox';
    lightbox.innerHTML = `
      <div class="lightbox-content">
        <img src="${src}" alt="${alt}">
        <button class="lightbox-close">&times;</button>
      </div>
    `;
    
    document.body.appendChild(lightbox);
    document.body.style.overflow = 'hidden';
    
    lightbox.querySelector('.lightbox-close').onclick = () => {
      document.body.removeChild(lightbox);
      document.body.style.overflow = '';
    };
  }
}

// Initialize when DOM loads
document.addEventListener('DOMContentLoaded', () => {
  new PortfolioApp();
  new ProjectInteractions();
  
  // PWA install prompt
  let deferredPrompt;
  window.addEventListener('beforeinstallprompt', (e) => {
    deferredPrompt = e;
  });
});

// Service Worker for offline portfolio viewing
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js')
      .then(reg => console.log('SW registered'))
      .catch(err => console.log('SW registration failed'));
  });
}

