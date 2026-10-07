document.addEventListener('DOMContentLoaded', () => {
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const revealElements = document.querySelectorAll(
        '.container > .section, .stat-card, .ruta-card, .card, .bus-card, .dashboard-empty-state'
    );

    document.body.classList.add('dashboard-motion-ready');
    revealElements.forEach((element) => element.classList.add('dashboard-reveal'));

    const animateStat = (stat) => {
        if (stat.dataset.countTarget) {
            return;
        }

        const value = stat.textContent.trim();
        if (!/^\d+$/.test(value)) {
            return;
        }

        const target = Number(value);
        stat.dataset.countTarget = value;
        if (prefersReducedMotion || target === 0) {
            return;
        }

        const duration = 900;
        const startTime = performance.now();
        const updateCount = (now) => {
            const progress = Math.min((now - startTime) / duration, 1);
            const easedProgress = 1 - Math.pow(1 - progress, 3);
            stat.textContent = String(Math.round(target * easedProgress));

            if (progress < 1) {
                window.requestAnimationFrame(updateCount);
            } else {
                stat.textContent = value;
            }
        };

        window.requestAnimationFrame(updateCount);
    };

    if (prefersReducedMotion || !('IntersectionObserver' in window)) {
        revealElements.forEach((element) => {
            element.classList.add('is-visible');
            element.querySelectorAll('.stat-value').forEach(animateStat);
        });
    } else {
        const revealObserver = new IntersectionObserver((entries, observer) => {
            entries.forEach((entry) => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('is-visible');
                    entry.target.querySelectorAll('.stat-value').forEach(animateStat);
                    observer.unobserve(entry.target);
                }
            });
        }, { threshold: 0.16, rootMargin: '0px 0px -32px 0px' });

        revealElements.forEach((element) => revealObserver.observe(element));
    }

    const modal = document.getElementById('contactModal');
    const openContactModalButtons = document.querySelectorAll('#openContactModalHero');
    const closeContactModal = document.getElementById('closeContactModal');
    const contactForm = document.getElementById('contactModalForm');
    const successAlert = document.getElementById('contactSuccessAlert');
    let lastModalTrigger = null;

    const toggleModal = (show) => {
        if (!modal) {
            return;
        }
        modal.hidden = !show;
        modal.setAttribute('aria-hidden', String(!show));
        document.body.classList.toggle('modal-open', show);

        if (show) {
            window.requestAnimationFrame(() => {
                modal.querySelector('input:not([type="hidden"])')?.focus();
            });
        } else if (lastModalTrigger) {
            lastModalTrigger.focus();
        }
    };

    openContactModalButtons.forEach((button) => {
        button.addEventListener('click', () => {
            lastModalTrigger = button;
            toggleModal(true);
        });
    });

    if (closeContactModal) {
        closeContactModal.addEventListener('click', () => toggleModal(false));
    }

    if (modal) {
        modal.addEventListener('click', (event) => {
            if (event.target === modal) {
                toggleModal(false);
            }
        });

        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && !modal.hidden) {
                toggleModal(false);
            }
        });
    }

    document.querySelectorAll('.scroll-to-section').forEach((link) => {
        link.addEventListener('click', (event) => {
            event.preventDefault();
            const targetId = link.dataset.target || link.getAttribute('href').slice(1);
            const target = targetId ? document.getElementById(targetId) : null;

            if (target) {
                target.scrollIntoView({ behavior: 'smooth' });
                history.replaceState(null, '', `#${targetId}`);
            }
        });
    });

    if (contactForm && successAlert) {
        contactForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            successAlert.textContent = '';
            successAlert.classList.add('hidden');

            const formData = new FormData(contactForm);

            try {
                const response = await fetch(contactForm.action, {
                    method: 'POST',
                    body: formData,
                });

                if (response.ok) {
                    successAlert.textContent = 'Mensaje enviado con éxito. Se cerrará en 2 segundos...';
                    successAlert.classList.remove('hidden');

                    setTimeout(() => {
                        toggleModal(false);
                        contactForm.reset();
                        successAlert.classList.add('hidden');
                    }, 2000);
                } else {
                    successAlert.textContent = 'No se pudo enviar. Intenta nuevamente.';
                    successAlert.classList.remove('hidden');
                }
            } catch (error) {
                successAlert.textContent = 'Error de red. Intenta nuevamente.';
                successAlert.classList.remove('hidden');
            }
        });
    }
});
