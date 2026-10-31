(() => {
    const form = document.getElementById("incidentForm");
    const description = document.getElementById("descripcion");
    const counter = document.getElementById("descripcion-contador");
    const submitButton = document.getElementById("incidentSubmit");
    const submitLabel = submitButton?.querySelector(".incident-submit-label");

    if (description && counter) {
        const maximum = Number.parseInt(description.getAttribute("maxlength"), 10);
        const updateCounter = () => {
            const length = description.value.length;
            counter.textContent = `${length} / ${maximum}`;
            counter.classList.toggle("incident-counter--near-limit", maximum - length <= maximum * 0.1);
        };

        description.addEventListener("input", updateCounter);
        updateCounter();
    }

    if (form && submitButton && submitLabel) {
        form.addEventListener("submit", (event) => {
            if (form.dataset.submitting === "true") {
                event.preventDefault();
                return;
            }

            form.dataset.submitting = "true";
            submitButton.dataset.submitting = "true";
            submitButton.setAttribute("aria-busy", "true");
            submitButton.disabled = true;
            submitLabel.textContent = "Enviando reporte...";
        });
    }
})();
