package com.fastmask.di

import com.fastmask.data.review.PlayReviewLauncher
import com.fastmask.data.review.ReviewLauncher
import com.fastmask.data.review.ReviewPrompter
import com.fastmask.domain.review.ReviewTrigger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {
    @Binds abstract fun bindLauncher(impl: PlayReviewLauncher): ReviewLauncher
    @Binds abstract fun bindTrigger(impl: ReviewPrompter): ReviewTrigger
}
