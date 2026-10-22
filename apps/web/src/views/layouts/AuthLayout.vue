<template>
    <div class="min-h-screen w-full flex items-center justify-center bg-bg-body relative overflow-hidden">
        <!-- Dynamic Background -->
        <div class="absolute inset-0 pointer-events-none">
            <div class="absolute top-0 left-1/4 w-96 h-96 bg-primary/20 rounded-full blur-[100px] animate-fade-in"></div>
            <div class="absolute bottom-0 right-1/4 w-96 h-96 bg-purple-500/20 rounded-full blur-[100px] animate-fade-in delay-100"></div>
        </div>

        <div class="w-full max-w-md z-10 p-4 animate-scale-in">
            <div class="flex justify-center mb-8">
                <h1 class="text-4xl font-display font-black bg-gradient-to-r from-primary to-purple-500 bg-clip-text text-transparent">
                    STUDY.AI
                </h1>
            </div>
            
            <div class="glass p-8 rounded-[32px] shadow-glass shadow-lg border border-border-glass">
                <slot></slot>
            </div>
            
            <p class="text-center text-xs text-text-muted mt-8 font-bold tracking-widest uppercase opacity-50">
                Powered by DeepSeek & Vue 3
            </p>
        </div>
    </div>
</template>

<style scoped>
.glass {
    background: rgba(255, 255, 255, 0.7);
    backdrop-filter: blur(20px);
}
:root.dark .glass {
    background: rgba(15, 23, 42, 0.6);
}
</style>
