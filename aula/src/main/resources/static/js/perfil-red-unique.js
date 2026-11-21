/* Unique JS for User Profile Red Theme */
(function(){
    // Dark mode toggle
    const toggleBtn = document.getElementById('darkModeToggle');
    if(toggleBtn){
        toggleBtn.addEventListener('click',()=>{
            document.body.classList.toggle('dark-mode');
        });
    }
    // Progress bar handling for form fields
    const progressBar = document.querySelector('.progress-bar');
    const form = document.querySelector('.profile-form');
    if(progressBar && form){
        const requiredInputs = form.querySelectorAll('input[required], textarea[required]');
        const total = requiredInputs.length;
        const updateProgress =()=>{
            const filled = Array.from(requiredInputs).filter(i=> i.value.trim() !== '').length;
            const percent = total===0 ? 0 : Math.round((filled/total)*100);
            progressBar.style.width = percent + '%';
        };
        requiredInputs.forEach(i=> i.addEventListener('input', updateProgress));
        // Initialize
        updateProgress();
    }
})();
