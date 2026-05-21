document.addEventListener('DOMContentLoaded', () => {
    const modal = document.getElementById('contactModal');
    const openContactModalCard = document.getElementById('openContactModalCard');
    const closeContactModal = document.getElementById('closeContactModal');
    const contactForm = document.getElementById('contactModalForm');
    const successAlert = document.getElementById('contactSuccessAlert');

    const toggleModal = (show) => {
        if (!modal) {
            return;
        }
        modal.classList.toggle('hidden', !show);
    };

    if (openContactModalCard) {
        openContactModalCard.addEventListener('click', () => toggleModal(true));
    }

    if (closeContactModal) {
        closeContactModal.addEventListener('click', () => toggleModal(false));
    }

    if (modal) {
        modal.addEventListener('click', (event) => {
            if (event.target === modal) {
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
