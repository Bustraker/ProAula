(() => {
    "use strict";

    const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");
    const contactFeedback = document.querySelector(".contact-feedback");
    if (contactFeedback) {
        window.addEventListener("load", () => {
            const contactSection = document.getElementById("contacto");
            if (contactSection) {
                contactSection.scrollIntoView({
                    behavior: motionPreference.matches ? "auto" : "smooth",
                    block: "start"
                });
            }
        });
    }

    const contactForm = document.querySelector(".contact-form");
    const contactProgress = contactForm?.querySelector(".contact-progress");
    if (contactForm && contactProgress) {
        const requiredFields = Array.from(contactForm.querySelectorAll(
            ".form-fields input[required], .form-fields textarea[required]"
        ));
        const progressFill = contactProgress.querySelector(".contact-progress-fill");
        const progressStatus = contactProgress.querySelector(".contact-progress-status");
        const progressStages = Array.from(contactProgress.querySelectorAll(".contact-progress-stage"));

        const updateContactProgress = () => {
            const completedFields = requiredFields.filter((field) => field.value.trim() && field.checkValidity());
            const completedCount = completedFields.length;
            const personalFieldsComplete = requiredFields
                .filter((field) => field.tagName !== "TEXTAREA")
                .every((field) => field.value.trim() && field.checkValidity());
            const messageComplete = requiredFields
                .filter((field) => field.tagName === "TEXTAREA")
                .every((field) => field.value.trim() && field.checkValidity());
            const focusedMessageField = document.activeElement?.tagName === "TEXTAREA";
            const activeStage = completedCount === requiredFields.length
                ? 2
                : focusedMessageField || personalFieldsComplete
                    ? 1
                    : 0;

            progressFill?.style.setProperty("--contact-progress", `${completedCount / requiredFields.length * 100}%`);
            contactProgress.setAttribute("aria-valuenow", String(completedCount));

            if (progressStatus) {
                progressStatus.textContent = completedCount === requiredFields.length
                    ? "Todo listo: puedes enviar tu mensaje"
                    : completedCount > 0
                        ? `${completedCount} de ${requiredFields.length} campos listos`
                        : "Completa tus datos para continuar";
            }

            progressStages.forEach((stage, index) => {
                stage.classList.toggle("is-current", index === activeStage);
                stage.classList.toggle("is-complete",
                    (index === 0 && personalFieldsComplete)
                    || (index === 1 && personalFieldsComplete && messageComplete)
                    || (index === 2 && completedCount === requiredFields.length));
            });
        };

        contactForm.addEventListener("input", updateContactProgress);
        contactForm.addEventListener("change", updateContactProgress);
        contactForm.addEventListener("focusin", updateContactProgress);
        contactForm.addEventListener("focusout", () => window.requestAnimationFrame(updateContactProgress));
        updateContactProgress();
    }

    if (motionPreference.matches) return;

    const revealTargets = document.querySelectorAll("[data-reveal]");
    if ("IntersectionObserver" in window && revealTargets.length > 0) {
        document.body.classList.add("motion-ready");

        const revealObserver = new IntersectionObserver((entries, observer) => {
            entries.forEach((entry) => {
                if (!entry.isIntersecting) return;

                entry.target.classList.add("is-visible");
                observer.unobserve(entry.target);
            });
        }, {
            rootMargin: "0px 0px -56px 0px",
            threshold: 0.08
        });

        revealTargets.forEach((target, index) => {
            const delay = Math.min((index % 3) * 90, 180);
            target.style.setProperty("--reveal-delay", `${delay}ms`);
            revealObserver.observe(target);
        });
    }

    const progressIndicator = document.querySelector(".scroll-progress");
    if (!progressIndicator) return;

    let progressFrame = 0;
    const updateProgress = () => {
        if (progressFrame) return;

        progressFrame = window.requestAnimationFrame(() => {
            progressFrame = 0;
            const documentRoot = document.documentElement;
            const scrollableDistance = documentRoot.scrollHeight - window.innerHeight;
            const progress = scrollableDistance > 0
                ? Math.min(1, documentRoot.scrollTop / scrollableDistance)
                : 0;

            progressIndicator.style.setProperty("--scroll-progress", progress.toFixed(4));
        });
    };

    window.addEventListener("scroll", updateProgress, { passive: true });
    window.addEventListener("resize", updateProgress, { passive: true });
    updateProgress();
})();