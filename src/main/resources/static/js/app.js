/* ==========================================================================
   GradeUp — app.js
   ==========================================================================

   WHAT THIS FILE IS FOR
   Every page works WITHOUT this file. The pages come fully built from the
   server (Thymeleaf). This script only adds niceties on top, such as:
     - the light/dark theme button
     - hiding pop-up messages after a few seconds
     - drag-and-drop for uploading PDFs
     - a "Working…" spinner on slow buttons
     - keyboard shortcuts in quizzes (1–4 to answer, Enter to continue)
     - the animated score circle on the results page
     - "Are you sure?" questions before deleting or quitting

   HOW THE HTML AND THIS FILE FIND EACH OTHER
   The HTML marks elements with "data-" attributes, for example:
       <button data-theme-toggle>
   and this file finds them with:
       document.querySelectorAll('[data-theme-toggle]')
   So if you want to know what a data- attribute in a page does, search for
   its name in this file.

   The whole file is wrapped in (function () { ... })(); so its variables
   don't clash with any other script on the page.
   ========================================================================== */
(function () {
    'use strict';   // makes JavaScript stricter, so mistakes show up as errors

    /* ======================================================================
       1. LIGHT / DARK THEME
       The chosen theme is saved in the browser's localStorage, so it is
       remembered next time. (layout.html also applies it before the page is
       drawn, to avoid a white flash.)
       ====================================================================== */
    const THEME_STORAGE_KEY = 'gradeup-theme';

    /** Switches the page to 'light' or 'dark' and updates the button's icon. */
    function applyTheme(theme) {
        // app.css has different colours for <html data-theme="dark">.
        document.documentElement.setAttribute('data-theme', theme);

        const buttons = document.querySelectorAll('[data-theme-toggle]');
        buttons.forEach(function (button) {
            const isDark = (theme === 'dark');

            // Screen readers read this label out loud.
            button.setAttribute('aria-label', isDark ? 'Switch to light theme' : 'Switch to dark theme');

            // In dark mode show the sun (to go back to light); in light mode show the moon.
            const sunIcon = button.querySelector('[data-icon-sun]');
            const moonIcon = button.querySelector('[data-icon-moon]');
            if (sunIcon) {
                sunIcon.style.display = isDark ? 'block' : 'none';
            }
            if (moonIcon) {
                moonIcon.style.display = isDark ? 'none' : 'block';
            }
        });
    }

    function setUpThemeButton() {
        // 1. Work out which theme to start with.
        let savedTheme = null;
        try {
            savedTheme = localStorage.getItem(THEME_STORAGE_KEY);
        } catch (error) {
            // Some browsers block localStorage (e.g. private mode). That's fine.
        }

        if (!savedTheme) {
            // Nothing saved yet: follow the computer's own light/dark setting.
            const computerPrefersDark = window.matchMedia &&
                window.matchMedia('(prefers-color-scheme: dark)').matches;
            savedTheme = computerPrefersDark ? 'dark' : 'light';
        }
        applyTheme(savedTheme);

        // 2. When the button is clicked, switch to the other theme and save the choice.
        const buttons = document.querySelectorAll('[data-theme-toggle]');
        buttons.forEach(function (button) {
            button.addEventListener('click', function () {
                const currentTheme = document.documentElement.getAttribute('data-theme');
                const newTheme = (currentTheme === 'dark') ? 'light' : 'dark';
                applyTheme(newTheme);

                try {
                    localStorage.setItem(THEME_STORAGE_KEY, newTheme);
                } catch (error) {
                    // Couldn't save it. The theme still changes for this page.
                }
            });
        });
    }

    /* ======================================================================
       2. POP-UP MESSAGES ("TOASTS")
       Messages like "Course created." fade out after about 5 seconds.
       ====================================================================== */
    const TOAST_VISIBLE_FOR_MS = 5200;   // ms = milliseconds (1000 ms = 1 second)

    function setUpToasts() {
        const toasts = document.querySelectorAll('[data-toast]');
        toasts.forEach(function (toast) {
            // setTimeout(fn, ms) runs fn once, after waiting ms milliseconds.
            setTimeout(function () {
                // Fade out and slide right...
                toast.style.transition = 'opacity 300ms ease, transform 300ms ease';
                toast.style.opacity = '0';
                toast.style.transform = 'translateX(16px)';

                // ...then remove it from the page once the animation has finished.
                setTimeout(function () {
                    toast.remove();
                }, 320);
            }, TOAST_VISIBLE_FOR_MS);
        });
    }

    /* ======================================================================
       3. PDF UPLOAD BOX (drag and drop)
       The box contains a normal <input type="file">. We let students drop a
       file onto the box, and show the chosen file's name and size.
       ====================================================================== */
    function setUpUploadBoxes() {
        const boxes = document.querySelectorAll('[data-dropzone]');
        boxes.forEach(function (box) {
            const fileInput = box.querySelector('input[type="file"]');
            const fileNameLabel = box.querySelector('[data-dropzone-filename]');
            if (!fileInput) {
                return;
            }

            // Clicking anywhere on the box opens the "choose file" window.
            box.addEventListener('click', function (event) {
                if (event.target !== fileInput) {
                    fileInput.click();
                }
            });

            // While a file is dragged over the box, highlight it.
            // preventDefault() stops the browser from simply opening the PDF itself.
            ['dragenter', 'dragover'].forEach(function (eventName) {
                box.addEventListener(eventName, function (event) {
                    event.preventDefault();
                    box.classList.add('dragover');
                });
            });

            // When the file leaves the box or is dropped, remove the highlight.
            ['dragleave', 'drop'].forEach(function (eventName) {
                box.addEventListener(eventName, function (event) {
                    event.preventDefault();
                    box.classList.remove('dragover');
                });
            });

            // When a file is dropped, put it into the real file input.
            box.addEventListener('drop', function (event) {
                const droppedFiles = event.dataTransfer && event.dataTransfer.files;
                if (droppedFiles && droppedFiles.length > 0) {
                    fileInput.files = droppedFiles;
                    fileInput.dispatchEvent(new Event('change'));   // so the label below updates
                }
            });

            // Whenever the chosen file changes, show "name · size MB".
            fileInput.addEventListener('change', function () {
                if (!fileNameLabel) {
                    return;
                }
                if (fileInput.files.length > 0) {
                    const file = fileInput.files[0];
                    const sizeInMegabytes = (file.size / (1024 * 1024)).toFixed(1);   // e.g. "2.4"
                    fileNameLabel.textContent = file.name + ' · ' + sizeInMegabytes + ' MB';
                    fileNameLabel.style.display = 'block';
                } else {
                    fileNameLabel.style.display = 'none';
                }
            });
        });
    }

    /* ======================================================================
       4. "WORKING…" SPINNER
       Forms marked data-loading (uploads, generating questions...) can take
       a while. We disable the button and show a spinner so students don't
       click it again.
       ====================================================================== */
    function setUpLoadingButtons() {
        const forms = document.querySelectorAll('form[data-loading]');
        forms.forEach(function (form) {
            form.addEventListener('submit', function () {
                const button = form.querySelector('button[type="submit"]');
                if (!button || button.disabled) {
                    return;
                }

                const originalButtonHtml = button.innerHTML;
                button.disabled = true;
                button.innerHTML = '<span class="spinner"></span> Working…';

                // Safety net: if the student comes back to this page with the browser's
                // Back button, the old page may be reused, so re-enable the button after 30s.
                setTimeout(function () {
                    button.disabled = false;
                    button.innerHTML = originalButtonHtml;
                }, 30000);
            });
        });
    }

    /* ======================================================================
       5. QUIZ KEYBOARD SHORTCUTS
         1, 2, 3, 4  (or A, B, C, D)  ->  choose that answer
         Enter                         ->  press the main button
                                           (Next question / Check answer / Finish)
       ====================================================================== */
    function setUpQuizKeyboard() {
        const quizForm = document.querySelector('[data-quiz-form]');
        if (!quizForm) {
            return;   // not on a quiz page
        }

        // Which key picks which answer.
        const keyToLetter = {
            '1': 'A', '2': 'B', '3': 'C', '4': 'D',
            'A': 'A', 'B': 'B', 'C': 'C', 'D': 'D'
        };

        document.addEventListener('keydown', function (event) {
            // Ignore key presses while typing in a text box or menu.
            // (Answer buttons are radio inputs, and those should still work.)
            if (event.target.matches('textarea, select, input:not([type="radio"])')) {
                return;
            }
            // Ignore shortcuts like Ctrl+C.
            if (event.metaKey || event.ctrlKey || event.altKey) {
                return;
            }

            const letter = keyToLetter[event.key.toUpperCase()];
            if (letter) {
                // Find the answer with that letter (skipping locked answers) and select it.
                const answer = quizForm.querySelector(
                    'input[type="radio"][value="' + letter + '"]:not(:disabled)');
                if (answer) {
                    answer.checked = true;
                    answer.dispatchEvent(new Event('change', { bubbles: true }));
                    event.preventDefault();
                }
                return;
            }

            // Enter presses the main button, but only once an answer has been chosen.
            if (event.key === 'Enter') {
                const mainButton = quizForm.querySelector('[data-quiz-primary]');
                const chosenAnswer = quizForm.querySelector('input[type="radio"]:checked');
                if (mainButton && chosenAnswer) {
                    event.preventDefault();
                    mainButton.click();
                }
            }
        });
    }

    /* ======================================================================
       6. ANIMATED SCORE CIRCLE (results page)
       The circle is an SVG <circle>. Its outline is drawn as one long dash;
       by moving where the dash starts ("stroke-dashoffset") we control how
       much of the circle is filled in. CSS animates the change.
       ====================================================================== */
    function setUpScoreCircle() {
        const ring = document.querySelector('[data-score-ring]');
        if (!ring) {
            return;   // not on the results page
        }

        const percentage = parseFloat(ring.getAttribute('data-score-ring')) || 0;
        const circle = ring.querySelector('.value');
        if (!circle) {
            return;
        }

        const radius = circle.r.baseVal.value;
        const circumference = 2 * Math.PI * radius;   // the length of the circle's outline

        // Start completely empty...
        circle.style.strokeDasharray = circumference;
        circle.style.strokeDashoffset = circumference;

        // ...then, a moment later, fill it up to the score.
        requestAnimationFrame(function () {
            setTimeout(function () {
                const emptyPart = circumference * (1 - percentage / 100);
                circle.style.strokeDashoffset = emptyPart;
            }, 120);
        });
    }

    /* ======================================================================
       7. "ARE YOU SURE?" QUESTIONS
         <form data-confirm="Delete this course?">   asks before sending the form
         <a data-confirm-link="Quit this quiz?">       asks before following the link
       If the student clicks Cancel, preventDefault() stops the action.
       ====================================================================== */
    function setUpConfirmations() {
        const forms = document.querySelectorAll('form[data-confirm]');
        forms.forEach(function (form) {
            form.addEventListener('submit', function (event) {
                const question = form.getAttribute('data-confirm');
                if (!window.confirm(question)) {
                    event.preventDefault();
                }
            });
        });

        const links = document.querySelectorAll('a[data-confirm-link]');
        links.forEach(function (link) {
            link.addEventListener('click', function (event) {
                const question = link.getAttribute('data-confirm-link') ||
                    'Leave this page? Your progress will be lost.';
                if (!window.confirm(question)) {
                    event.preventDefault();
                }
            });
        });
    }

    /* ======================================================================
       8. COURSE COLOUR PICKER (new/edit course form)
       Moves the "selected" ring to whichever colour was just picked.
       ====================================================================== */
    function setUpColourPicker() {
        const picker = document.querySelector('[data-color-picker]');
        if (!picker) {
            return;
        }

        const colourOptions = picker.querySelectorAll('input[type="radio"]');
        colourOptions.forEach(function (option) {
            option.addEventListener('change', function () {
                // Remove the ring from every colour...
                picker.querySelectorAll('.swatch').forEach(function (swatch) {
                    swatch.classList.remove('selected');
                });
                // ...and add it to the one that was picked.
                const pickedSwatch = option.closest('.swatch');
                if (pickedSwatch) {
                    pickedSwatch.classList.add('selected');
                }
            });
        });
    }

    /* ======================================================================
       START EVERYTHING
       We wait until the page's HTML has fully loaded, otherwise
       querySelector() might not find the elements yet.
       ====================================================================== */
    function startAll() {
        setUpThemeButton();
        setUpToasts();
        setUpUploadBoxes();
        setUpLoadingButtons();
        setUpQuizKeyboard();
        setUpScoreCircle();
        setUpConfirmations();
        setUpColourPicker();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', startAll);   // still loading: wait
    } else {
        startAll();                                                // already loaded: go
    }
})();
