document.addEventListener('DOMContentLoaded', function() {
    const redirectUrl = document.body.dataset.redirectUrl;
    const timeout = parseInt(document.body.dataset.redirectTimeout, 10);
    if (!redirectUrl || Number.isNaN(timeout)) return;

    setTimeout(function() {
        window.location.href = redirectUrl;
    }, timeout);
});
