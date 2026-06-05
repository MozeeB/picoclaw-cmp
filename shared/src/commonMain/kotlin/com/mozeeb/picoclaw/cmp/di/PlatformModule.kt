package com.mozeeb.picoclaw.cmp.di

import org.koin.core.module.Module

/**
 * Each platform provides its own Koin module containing:
 * - The platform-specific [CoreServiceAdapter] binding
 * - The DataStore<Preferences> binding
 */
expect fun platformModule(): Module
