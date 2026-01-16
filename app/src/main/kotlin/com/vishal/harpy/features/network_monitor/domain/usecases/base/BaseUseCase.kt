package com.vishal.harpy.features.network_monitor.domain.usecases.base

abstract class UseCase<out Type, in Params> {
    abstract suspend operator fun invoke(params: Params): Type
}

abstract class NoneParamUseCase<out Type> {
    abstract suspend operator fun invoke(): Type
}