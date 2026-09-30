(() => {
    "use strict";

    const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");
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