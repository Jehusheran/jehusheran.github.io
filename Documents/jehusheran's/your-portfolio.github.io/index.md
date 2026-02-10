---
layout: default
title: Your Name - AI/ML IoT Engineer
description: Smart Bus monitoring, YOLO CV, Flutter apps
---
<section id="home">Your hero content...</section>
<section id="about">Your bio...</section>
---
<!-- Skills Section -->
<section id="skills">
  <div class="container">
    <h2>Technical Skills</h2>
    
    {% for category in site.data.skills.skills %}
      <div class="skill-category">
        <div class="category-header">
          <i class="{{ category.icon }}"></i>
          <h3>{{ category.category }}</h3>
        </div>
        
        {% for skill in category.skills %}
          <div class="skill-item">
            <div class="skill-info">
              <i class="{{ skill.icon }}"></i>
              <span>{{ skill.name }}</span>
            </div>
            <div class="skill-bar">
              <div class="skill-progress" 
                   style="width: {{ skill.proficiency }}%; 
                          background-color: {{ category.color }};">
              </div>
            </div>
            <span class="skill-percent">{{ skill.proficiency }}%</span>
          </div>
        {% endfor %}
      </div>
    {% endfor %}
  </div>
</section>
