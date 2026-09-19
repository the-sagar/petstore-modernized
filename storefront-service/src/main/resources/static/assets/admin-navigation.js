(() => {
    'use strict';
    const form = document.getElementById('admin-logout-form');
    if (!form) return;
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const button = form.querySelector('button');
        button.disabled = true;
        try {
            const response = await fetch(form.action, {
                method: 'POST', credentials: 'same-origin', body: new URLSearchParams(new FormData(form))
            });
            if (!response.ok) throw new Error();
            window.location.assign('/login');
        } catch {
            document.getElementById('admin-nav-message').textContent = 'Unable to sign out. Reload and try again.';
            button.disabled = false;
        }
    });
})();
